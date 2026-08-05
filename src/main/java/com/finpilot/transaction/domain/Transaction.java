package com.finpilot.transaction.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Normalized Transaction Contract.
 *
 * Every AI module (categorization, forecasting, health score, LLM copilot)
 * reads ONLY from this table, never from source-specific data. See
 * V2__normalized_transaction_contract.sql for the full design rationale,
 * particularly around the two-level dedup strategy.
 *
 * amount is intentionally always non-negative — {@link #direction} carries
 * the sign meaning. Never infer sign from amount; different import sources
 * use inconsistent conventions and that's a classic source of bank-import bugs.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "schema_version", nullable = false)
    private short schemaVersion = 1;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "import_batch_id")
    private UUID importBatchId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source_type", nullable = false, columnDefinition = "import_source_type")
    private TransactionEnums.ImportSourceType sourceType;

    @Column(name = "external_transaction_id")
    private String externalTransactionId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "direction", nullable = false, columnDefinition = "transaction_direction")
    private TransactionEnums.Direction direction;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "transaction_status")
    private TransactionEnums.TransactionStatus status = TransactionEnums.TransactionStatus.POSTED;

    @Column(name = "merchant_raw", nullable = false, columnDefinition = "TEXT")
    private String merchantRaw;

    @Column(name = "merchant_normalized", columnDefinition = "TEXT")
    private String merchantNormalized;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "category_confidence", precision = 4, scale = 3)
    private BigDecimal categoryConfidence;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "category_source", columnDefinition = "categorization_source")
    private TransactionEnums.CategorizationSource categorySource;

    @Column(name = "category_corrected_at")
    private Instant categoryCorrectedAt;

    @Column(name = "import_confidence", precision = 4, scale = 3)
    private BigDecimal importConfidence;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "reversal_of_transaction_id")
    private UUID reversalOfTransactionId;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
        // JPA
    }

    /**
     * Primary construction path — used by import connectors after they've
     * mapped source-specific data into this contract. category fields are
     * deliberately absent here: categorization happens as a distinct
     * pipeline step (Phase 3/4), never at creation time.
     */
    public Transaction(UUID userId, UUID accountId, UUID importBatchId,
                       TransactionEnums.ImportSourceType sourceType,
                       LocalDate transactionDate, BigDecimal amount, String currency,
                       TransactionEnums.Direction direction, String merchantRaw,
                       String description, String fingerprint) {
        this.userId = userId;
        this.accountId = accountId;
        this.importBatchId = importBatchId;
        this.sourceType = sourceType;
        this.transactionDate = transactionDate;
        this.amount = amount;
        this.currency = currency;
        this.direction = direction;
        this.merchantRaw = merchantRaw;
        this.description = description;
        this.fingerprint = fingerprint;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Applies a categorization result (from the AI service, or a manual
     * correction). This is the ONLY way category fields should be set —
     * keeping it as an explicit method means we always know source and
     * confidence travel together and never drift out of sync.
     */
    public void applyCategorization(UUID categoryId, BigDecimal confidence,
                                    TransactionEnums.CategorizationSource source) {
        this.categoryId = categoryId;
        this.categoryConfidence = confidence;
        this.categorySource = source;
        if (source == TransactionEnums.CategorizationSource.USER_CORRECTED) {
            this.categoryCorrectedAt = Instant.now();
        }
    }

    public void softDelete() {
        this.deleted = true;
    }

    // --- Getters only for immutable facts; category/deletion mutate via explicit methods above ---

    public UUID getId() { return id; }
    public short getSchemaVersion() { return schemaVersion; }
    public UUID getUserId() { return userId; }
    public UUID getAccountId() { return accountId; }
    public UUID getImportBatchId() { return importBatchId; }
    public TransactionEnums.ImportSourceType getSourceType() { return sourceType; }
    public String getExternalTransactionId() { return externalTransactionId; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public Instant getPostedAt() { return postedAt; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public TransactionEnums.Direction getDirection() { return direction; }
    public TransactionEnums.TransactionStatus getStatus() { return status; }
    public String getMerchantRaw() { return merchantRaw; }
    public String getMerchantNormalized() { return merchantNormalized; }
    public void setMerchantNormalized(String merchantNormalized) { this.merchantNormalized = merchantNormalized; }
    public String getDescription() { return description; }
    public UUID getCategoryId() { return categoryId; }
    public BigDecimal getCategoryConfidence() { return categoryConfidence; }
    public TransactionEnums.CategorizationSource getCategorySource() { return categorySource; }
    public Instant getCategoryCorrectedAt() { return categoryCorrectedAt; }
    public BigDecimal getImportConfidence() { return importConfidence; }
    public void setImportConfidence(BigDecimal importConfidence) { this.importConfidence = importConfidence; }
    public String getFingerprint() { return fingerprint; }
    public UUID getReversalOfTransactionId() { return reversalOfTransactionId; }
    public void setReversalOfTransactionId(UUID reversalOfTransactionId) { this.reversalOfTransactionId = reversalOfTransactionId; }
    public boolean isDeleted() { return deleted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}