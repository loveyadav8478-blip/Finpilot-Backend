package com.finpilot.importengine.service;

import com.finpilot.importengine.csv.*;
import com.finpilot.importengine.dto.ImportResultResponse;
import com.finpilot.transaction.domain.ImportBatch;
import com.finpilot.transaction.domain.Transaction;
import com.finpilot.transaction.domain.TransactionEnums;
import com.finpilot.transaction.repository.ImportBatchRepository;
import com.finpilot.transaction.repository.TransactionRepository;
import com.finpilot.transaction.service.FingerprintService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates a CSV import: batch-level dedup -> parse -> normalize ->
 * row-level dedup -> persist.
 *
 * NOTE on synchronicity: CSV files are small/fast enough to process
 * synchronously within the HTTP request for Phase 2 (unlike PDF, which the
 * roadmap correctly calls out as needing an async job queue due to
 * OCR/parsing latency). This method is still written as a single
 * self-contained unit with no dependency on the HTTP request/response
 * cycle, specifically so it can be moved behind a background job runner
 * later without restructuring — if CSV files start arriving at a size
 * where synchronous processing risks request timeouts, wrap a call to
 * {@link #importCsv} in a queued job rather than rewriting this method.
 */
@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);
    private static final String DEFAULT_CURRENCY = "INR";

    private final ImportBatchRepository importBatchRepository;
    private final TransactionRepository transactionRepository;
    private final FingerprintService fingerprintService;

    public CsvImportService(ImportBatchRepository importBatchRepository,
                            TransactionRepository transactionRepository,
                            FingerprintService fingerprintService) {
        this.importBatchRepository = importBatchRepository;
        this.transactionRepository = transactionRepository;
        this.fingerprintService = fingerprintService;
    }

    @Transactional
    public ImportResultResponse importCsv(UUID userId, UUID accountId, String originalFilename, byte[] fileBytes) {
        String fileHash = sha256Hex(fileBytes);

        // Batch-level dedup — reject re-uploads before we parse a single row.
        Optional<ImportBatch> existing = importBatchRepository.findByUserIdAndFileHash(userId, fileHash);
        if (existing.isPresent()) {
            log.warn("This file was already imported (batch " + existing.get().getId() + "). Re-upload rejected.");
            throw new DuplicateImportException(
                    "This file was already imported (batch " + existing.get().getId() + "). Re-upload rejected.");
        }

        ImportBatch batch = new ImportBatch(userId, accountId, TransactionEnums.ImportSourceType.CSV, fileHash, originalFilename);
        batch = importBatchRepository.save(batch);

        String content = new String(fileBytes, StandardCharsets.UTF_8);
        List<List<String>> rows = CsvParser.parse(content);

        if (rows.isEmpty()) {
            batch.markCompleted(0, 0, 0);
            batch.setErrorSummary("File was empty or unparseable");
            importBatchRepository.save(batch);
            return toResponse(batch, 0, List.of());
        }

        Optional<CsvColumnMapping> mappingOpt = CsvColumnMapping.autoDetect(rows.get(0));
        if (mappingOpt.isEmpty()) {
            String reason = "Could not auto-detect required columns (date, description, amount) from header: "
                    + String.join(", ", rows.get(0));

            batch.markCompleted(0, 0, rows.size() - 1);
            batch.setErrorSummary(reason);
            importBatchRepository.save(batch);

            // One synthetic error entry so the caller can see the real
            // reason without a DB query. There's no per-row detail here
            // since we never got past the header — this represents the
            // whole file as a single failure.
            List<RowValidationError> headerError = List.of(
                    new RowValidationError(0, String.join(",", rows.get(0)), reason));
            return toResponse(batch, 0, headerError);
        }

        batch.setTotalRowsDetected(rows.size() - 1);

        CsvRowNormalizer.NormalizationResult normalized =
                CsvRowNormalizer.normalizeAll(rows, mappingOpt.get(), DEFAULT_CURRENCY);

        FingerprintService.OccurrenceTracker occurrenceTracker = new FingerprintService.OccurrenceTracker();
        int imported = 0;
        int skippedDuplicate = 0;
        List<RowValidationError> allErrors = new ArrayList<>(normalized.errors());

        for (NormalizedTransactionDraft draft : normalized.drafts()) {
            try {
                int occurrenceIndex = occurrenceTracker.next(
                        draft.transactionDate(), draft.amount(), draft.direction(),
                        FingerprintService.normalizeMerchant(draft.merchantRaw()));

                String fingerprint = fingerprintService.computeFingerprint(
                        userId, draft.transactionDate(), draft.amount(), draft.direction(),
                        draft.merchantRaw(), occurrenceIndex);

                if (transactionRepository.findByUserIdAndFingerprint(userId, fingerprint).isPresent()) {
                    skippedDuplicate++;
                    continue;
                }

                Transaction transaction = new Transaction(
                        userId, accountId, batch.getId(),
                        TransactionEnums.ImportSourceType.CSV,
                        draft.transactionDate(), draft.amount(), draft.currency(),
                        TransactionEnums.Direction.valueOf(draft.direction()),
                        draft.merchantRaw(), draft.description(), fingerprint);

                transactionRepository.save(transaction);
                imported++;
            } catch (Exception e) {
                log.error("Unexpected error persisting row {} of batch {}", draft.sourceRowNumber(), batch.getId(), e);
                allErrors.add(new RowValidationError(draft.sourceRowNumber(), draft.merchantRaw(),
                        "Internal error while saving row: " + e.getMessage()));
            }
        }

        batch.markCompleted(imported, skippedDuplicate, allErrors.size());
        if (!allErrors.isEmpty()) {
            batch.setErrorSummary(allErrors.size() + " row(s) failed — see error detail in response");
        }
        importBatchRepository.save(batch);

        log.info("CSV import batch {} for user {}: {} imported, {} duplicate, {} failed",
                batch.getId(), userId, imported, skippedDuplicate, allErrors.size());

        return toResponse(batch, imported, allErrors);
    }

    private ImportResultResponse toResponse(ImportBatch batch, int imported, List<RowValidationError> errors) {
        return new ImportResultResponse(
                batch.getId(),
                batch.getStatus().name(),
                batch.getTotalRowsDetected() == null ? 0 : batch.getTotalRowsDetected(),
                imported,
                batch.getRowsSkippedDuplicate(),
                errors.size(),
                batch.getErrorSummary(),
                errors
        );
    }

    private static String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}