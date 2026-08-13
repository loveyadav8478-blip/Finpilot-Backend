-- V2__normalized_transaction_contract.sql
--
-- The single most important table in the system. Every AI module
-- (categorization, forecasting, health score, LLM copilot) reads from
-- this table and this table only — it never queries source-specific data.

-- ============================================================
-- IMPORT BATCHES
-- One row per "import event" (one CSV upload, one PDF upload, one manual
-- entry session). Batch-level dedup happens here via file_hash, BEFORE
-- any parsing occurs — this is the first line of defense against a user
-- accidentally re-uploading the same statement.
-- ============================================================
CREATE TYPE import_source_type AS ENUM (
    'CSV', 'PDF', 'MANUAL', 'SMS', 'BANK_API', 'ACCOUNT_AGGREGATOR', 'WALLET'
);

CREATE TYPE import_batch_status AS ENUM (
    'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'PARTIALLY_FAILED'
);

CREATE TABLE import_batches (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id          UUID REFERENCES accounts(id) ON DELETE SET NULL,
    source_type         import_source_type NOT NULL,

    -- SHA-256 of the raw uploaded file bytes. NULL for MANUAL and API-driven
    -- sources where there's no "file" to hash.
    file_hash           VARCHAR(64),
    original_filename   VARCHAR(500),

    status              import_batch_status NOT NULL DEFAULT 'PENDING',
    total_rows_detected INTEGER,
    rows_imported       INTEGER NOT NULL DEFAULT 0,
    rows_skipped_duplicate INTEGER NOT NULL DEFAULT 0,
    rows_failed         INTEGER NOT NULL DEFAULT 0,
    error_summary       TEXT, -- human-readable summary; row-level errors go in import_batch_errors (future phase: import error reporting)

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);

-- Batch-level idempotency: the same user re-uploading byte-identical file
-- content is rejected here, before we ever parse a row.
CREATE UNIQUE INDEX uq_import_batches_user_file_hash
    ON import_batches (user_id, file_hash)
    WHERE file_hash IS NOT NULL;

CREATE INDEX idx_import_batches_user_status ON import_batches (user_id, status);


-- ============================================================
-- TRANSACTIONS — the Normalized Transaction Contract
-- ============================================================
CREATE TYPE transaction_direction AS ENUM ('DEBIT', 'CREDIT');

CREATE TYPE transaction_status AS ENUM ('PENDING', 'POSTED');
-- PENDING exists for future bank-API sources where amounts can still
-- change before settling. CSV/PDF/manual imports are always POSTED.

CREATE TYPE categorization_source AS ENUM ('RULE', 'ML_MODEL', 'USER_CORRECTED', 'FALLBACK');

CREATE TABLE transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schema_version          SMALLINT NOT NULL DEFAULT 1,

    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id              UUID REFERENCES accounts(id) ON DELETE SET NULL,
    import_batch_id         UUID REFERENCES import_batches(id) ON DELETE SET NULL,

    -- Provenance: what kind of source produced this row, independent of
    -- which specific batch. Kept even if the batch is later deleted.
    source_type             import_source_type NOT NULL,
    external_transaction_id VARCHAR(255), -- the source system's own ID, when one exists (future: bank APIs)

    -- Core financial facts
    transaction_date        DATE NOT NULL,           -- the date the user experienced the spend (no time component — avoids TZ off-by-one bugs)
    posted_at               TIMESTAMPTZ,              -- when it actually cleared, if known/different
    amount                  NUMERIC(19,4) NOT NULL CHECK (amount >= 0), -- always non-negative; direction carries the sign meaning
    currency                VARCHAR(3) NOT NULL DEFAULT 'INR', -- ISO 4217
    direction               transaction_direction NOT NULL,
    status                  transaction_status NOT NULL DEFAULT 'POSTED',

    -- Descriptive fields
    merchant_raw            TEXT NOT NULL,            -- exactly as it appeared in the source
    merchant_normalized     TEXT,                      -- cleaned/canonicalized form, filled by the normalization step
    description             TEXT,

    -- Categorization (owned by the AI service's decision, stored here)
    category_id             UUID REFERENCES categories(id),
    category_confidence     NUMERIC(4,3) CHECK (category_confidence BETWEEN 0 AND 1),
    category_source         categorization_source,
    category_corrected_at   TIMESTAMPTZ,              -- non-null if a user manually corrected the category — this is the feedback-loop signal for retraining

    -- Data-quality signal, distinct from category confidence. Relevant for
    -- OCR'd PDF imports where the amount/date/merchant text itself might
    -- be misread. NULL for sources with no inherent read-uncertainty
    -- (manual entry, CSV, structured APIs).
    import_confidence       NUMERIC(4,3) CHECK (import_confidence BETWEEN 0 AND 1),

    -- Idempotency
    fingerprint              VARCHAR(64) NOT NULL,

    -- Reversals reference the original transaction rather than mutating it,
    -- preserving the audit trail.
    reversal_of_transaction_id UUID REFERENCES transactions(id),

    is_deleted               BOOLEAN NOT NULL DEFAULT FALSE, -- soft delete for audit; hard PII erasure is a separate, later compliance workflow
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Row-level idempotency, scoped per user (not global — two different
-- users' fingerprints coincidentally colliding is possible and should not
-- cause a false dedupe).
CREATE UNIQUE INDEX uq_transactions_user_fingerprint
    ON transactions (user_id, fingerprint);

-- Query patterns this needs to serve fast, in rough order of frequency:
-- 1. "all of this user's transactions in a date range" (dashboard, reports)
-- 2. "all of this user's transactions in a category" (category drill-down)
-- 3. "all transactions from one import batch" (import review/undo)
CREATE INDEX idx_transactions_user_date ON transactions (user_id, transaction_date DESC) WHERE is_deleted = FALSE;
CREATE INDEX idx_transactions_user_category ON transactions (user_id, category_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_transactions_import_batch ON transactions (import_batch_id);

-- Uncategorized-transactions queue (feeds the categorization pipeline)
CREATE INDEX idx_transactions_uncategorized ON transactions (user_id) WHERE category_id IS NULL AND is_deleted = FALSE;

COMMENT ON TABLE transactions IS
    'The Normalized Transaction Contract. Every AI module reads only from this table, never from source-specific data. See fingerprint column for the two-level dedup strategy (batch-level file_hash + row-level fingerprint with occurrence disambiguation).';
