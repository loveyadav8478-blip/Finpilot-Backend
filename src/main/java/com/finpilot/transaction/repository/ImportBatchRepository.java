package com.finpilot.transaction.repository;

import com.finpilot.transaction.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.*;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    /**
     * Batch-level idempotency check — call this BEFORE parsing an uploaded
     * file. If a batch with this (userId, fileHash) already exists, reject
     * the upload immediately rather than re-processing it.
     */
    List<ImportBatch> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<ImportBatch> findByUserIdAndFileHash(UUID userId, String fileHash);
}
