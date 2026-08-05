package com.finpilot.transaction.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per import event (one file upload, or one manual-entry session).
 * Batch-level dedup lives here via {@code fileHash} — see the unique index
 * uq_import_batches_user_file_hash in V2__normalized_transaction_contract.sql.
 */
@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id")
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source_type", nullable = false, columnDefinition = "import_source_type")
    private TransactionEnums.ImportSourceType sourceType;

    /** SHA-256 hex of the raw uploaded file bytes. Null for MANUAL/API sources. */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "import_batch_status")
    private TransactionEnums.ImportBatchStatus status = TransactionEnums.ImportBatchStatus.PENDING;

    @Column(name = "total_rows_detected")
    private Integer totalRowsDetected;

    @Column(name = "rows_imported", nullable = false)
    private int rowsImported = 0;

    @Column(name = "rows_skipped_duplicate", nullable = false)
    private int rowsSkippedDuplicate = 0;

    @Column(name = "rows_failed", nullable = false)
    private int rowsFailed = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ImportBatch() {
        // JPA
    }

    public ImportBatch(UUID userId, UUID accountId, TransactionEnums.ImportSourceType sourceType,
                        String fileHash, String originalFilename) {
        this.userId = userId;
        this.accountId = accountId;
        this.sourceType = sourceType;
        this.fileHash = fileHash;
        this.originalFilename = originalFilename;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void markCompleted(int imported, int skippedDuplicate, int failed) {
        this.rowsImported = imported;
        this.rowsSkippedDuplicate = skippedDuplicate;
        this.rowsFailed = failed;
        this.status = failed > 0 && imported > 0
                ? TransactionEnums.ImportBatchStatus.PARTIALLY_FAILED
                : failed > 0 ? TransactionEnums.ImportBatchStatus.FAILED
                : TransactionEnums.ImportBatchStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    // --- Getters (no setters beyond the explicit domain methods above —
    // batch status transitions should go through markCompleted(), not ad-hoc mutation) ---

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getAccountId() { return accountId; }
    public TransactionEnums.ImportSourceType getSourceType() { return sourceType; }
    public String getFileHash() { return fileHash; }
    public String getOriginalFilename() { return originalFilename; }
    public TransactionEnums.ImportBatchStatus getStatus() { return status; }
    public Integer getTotalRowsDetected() { return totalRowsDetected; }
    public void setTotalRowsDetected(Integer totalRowsDetected) { this.totalRowsDetected = totalRowsDetected; }
    public int getRowsImported() { return rowsImported; }
    public int getRowsSkippedDuplicate() { return rowsSkippedDuplicate; }
    public int getRowsFailed() { return rowsFailed; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}


