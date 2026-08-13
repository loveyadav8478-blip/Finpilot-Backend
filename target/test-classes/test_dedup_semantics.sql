-- test_dedup_semantics.sql
-- Proves the two-level dedup strategy actually behaves as designed.

\set ON_ERROR_STOP off

-- Setup: one user, one account
INSERT INTO users (id, email) VALUES ('11111111-1111-1111-1111-111111111111', 'test@finpilot.dev');
INSERT INTO accounts (id, user_id, display_name) VALUES ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'HDFC Savings');

\echo '--- TEST 1: batch-level dedup — same file_hash for same user should be rejected ---'
INSERT INTO import_batches (id, user_id, account_id, source_type, file_hash, original_filename)
VALUES ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'PDF', repeat('a', 64), 'statement_july.pdf');

-- This second insert with the SAME file_hash for the SAME user must fail
INSERT INTO import_batches (id, user_id, account_id, source_type, file_hash, original_filename)
VALUES ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'PDF', repeat('a', 64), 'statement_july_reupload.pdf');

\echo '--- TEST 2: same file_hash for a DIFFERENT user must succeed (dedup is per-user, not global) ---'
INSERT INTO users (id, email) VALUES ('55555555-5555-5555-5555-555555555555', 'other-user@finpilot.dev');
INSERT INTO import_batches (id, user_id, source_type, file_hash, original_filename)
VALUES ('66666666-6666-6666-6666-666666666666', '55555555-5555-5555-5555-555555555555', 'PDF', repeat('a', 64), 'their_statement.pdf');

\echo '--- TEST 3: row-level fingerprint — two IDENTICAL transactions in one batch (occurrence_index disambiguates) must BOTH survive ---'
-- Simulates two identical ₹200 Swiggy orders on the same day: same fields,
-- but fingerprints differ because occurrence_index (baked in at parse time) differs.
INSERT INTO transactions (user_id, account_id, import_batch_id, source_type, transaction_date, amount, currency, direction, merchant_raw, fingerprint)
VALUES
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'PDF', '2026-07-01', 200.00, 'INR', 'DEBIT', 'SWIGGY*ORDER 001', encode(sha256('11111111-1111-1111-1111-111111111111|2026-07-01|200.00|DEBIT|SWIGGY ORDER|0'::bytea), 'hex')),
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'PDF', '2026-07-01', 200.00, 'INR', 'DEBIT', 'SWIGGY*ORDER 002', encode(sha256('11111111-1111-1111-1111-111111111111|2026-07-01|200.00|DEBIT|SWIGGY ORDER|1'::bytea), 'hex'));

\echo '--- TEST 4: re-processing the SAME batch (idempotent retry) regenerates identical fingerprints -> must be REJECTED as duplicates, not double-inserted ---'
INSERT INTO transactions (user_id, account_id, import_batch_id, source_type, transaction_date, amount, currency, direction, merchant_raw, fingerprint)
VALUES
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'PDF', '2026-07-01', 200.00, 'INR', 'DEBIT', 'SWIGGY*ORDER 001 (retry)', encode(sha256('11111111-1111-1111-1111-111111111111|2026-07-01|200.00|DEBIT|SWIGGY ORDER|0'::bytea), 'hex'));

\echo '--- RESULTS ---'
SELECT 'import_batches count for user 1' AS check, count(*) FROM import_batches WHERE user_id = '11111111-1111-1111-1111-111111111111';
SELECT 'transactions count for user 1 (expect 2, not 3)' AS check, count(*) FROM transactions WHERE user_id = '11111111-1111-1111-1111-111111111111';
SELECT 'transactions count for user 2 (expect 1)' AS check, count(*) FROM transactions WHERE user_id = '55555555-5555-5555-5555-555555555555';
