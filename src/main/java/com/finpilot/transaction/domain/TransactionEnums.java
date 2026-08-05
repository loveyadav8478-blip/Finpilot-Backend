package com.finpilot.transaction.domain;

/**
 * Mirrors the Postgres enum types created in V2__normalized_transaction_contract.sql.
 * Keeping these as first-class Java enums (rather than raw strings) gives us
 * compile-time safety anywhere a transaction is categorized, imported, or displayed.
 */
public final class TransactionEnums {

    private TransactionEnums() {
    }

    public enum ImportSourceType {
        CSV, PDF, MANUAL, SMS, BANK_API, ACCOUNT_AGGREGATOR, WALLET
    }

    public enum ImportBatchStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, PARTIALLY_FAILED
    }

    public enum Direction {
        DEBIT, CREDIT
    }

    public enum TransactionStatus {
        PENDING, POSTED
    }

    /**
     * How a transaction's category was determined. USER_CORRECTED is the
     * signal the categorization retraining feedback loop (Phase 4) reads —
     * every row where a human overrode the AI's guess becomes future
     * training data.
     */
    public enum CategorizationSource {
        RULE, ML_MODEL, USER_CORRECTED, FALLBACK
    }
}
