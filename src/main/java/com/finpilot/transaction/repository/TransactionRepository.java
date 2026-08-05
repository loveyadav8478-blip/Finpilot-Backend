package com.finpilot.transaction.repository;

import com.finpilot.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * The idempotency check every import connector calls before inserting a
     * row. If present, skip (increment rowsSkippedDuplicate on the batch)
     * rather than insert — never rely solely on catching the DB unique
     * constraint violation, since that's slower and messier to handle in bulk.
     */
    Optional<Transaction> findByUserIdAndFingerprint(UUID userId, String fingerprint);

    List<Transaction> findByUserIdAndTransactionDateBetweenAndDeletedFalse(
            UUID userId, LocalDate startInclusive, LocalDate endInclusive);

    List<Transaction> findByImportBatchId(UUID importBatchId);

    /**
     * Feeds the categorization pipeline (Phase 4) — transactions imported
     * but not yet categorized.
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.categoryId IS NULL AND t.deleted = false")
    List<Transaction> findUncategorized(@Param("userId") UUID userId);

    long countByUserIdAndDeletedFalse(UUID userId);
}
