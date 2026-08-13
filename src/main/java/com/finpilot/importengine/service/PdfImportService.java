package com.finpilot.importengine.service;

import com.finpilot.importengine.csv.NormalizedTransactionDraft;
import com.finpilot.importengine.csv.RowValidationError;
import com.finpilot.importengine.dto.ImportResultResponse;
import com.finpilot.importengine.pdf.PdfStatementLineParser;
import com.finpilot.importengine.pdf.PdfTextExtractor;
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

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates a PDF bank statement import: batch-level dedup -> text extraction ->
 * line parsing -> row-level dedup -> persist.
 */
@Service
public class PdfImportService {

    private static final Logger log = LoggerFactory.getLogger(PdfImportService.class);
    private static final String DEFAULT_CURRENCY = "INR";

    private final ImportBatchRepository importBatchRepository;
    private final TransactionRepository transactionRepository;
    private final FingerprintService fingerprintService;
    private final PdfTextExtractor pdfTextExtractor;
    private final PdfStatementLineParser pdfStatementLineParser;

    public PdfImportService(ImportBatchRepository importBatchRepository,
                            TransactionRepository transactionRepository,
                            FingerprintService fingerprintService,
                            PdfTextExtractor pdfTextExtractor) {
        this.importBatchRepository = importBatchRepository;
        this.transactionRepository = transactionRepository;
        this.fingerprintService = fingerprintService;
        this.pdfTextExtractor = pdfTextExtractor;
        this.pdfStatementLineParser = new PdfStatementLineParser();
    }

    @Transactional
    public ImportResultResponse importPdf(UUID userId, UUID accountId, String originalFilename, byte[] pdfBytes, String password) throws IOException {
        String fileHash = sha256Hex(pdfBytes);

        // Batch-level dedup — reject re-uploads before parsing
        Optional<ImportBatch> existing = importBatchRepository.findByUserIdAndFileHash(userId, fileHash);
        if (existing.isPresent()) {
            log.warn("This PDF file was already imported (batch {}). Re-upload rejected.", existing.get().getId());
            throw new DuplicateImportException(
                    "This PDF file was already imported (batch " + existing.get().getId() + "). Re-upload rejected.");
        }

        ImportBatch batch = new ImportBatch(userId, accountId, TransactionEnums.ImportSourceType.PDF, fileHash, originalFilename);
        batch = importBatchRepository.save(batch);

        String text;
        try {
            text = pdfTextExtractor.extractText(pdfBytes, password);
        } catch (Exception e) {
            batch.markCompleted(0, 0, 1);
            batch.setErrorSummary("Failed to extract text from PDF: " + e.getMessage());
            importBatchRepository.save(batch);
            throw e;
        }

        PdfStatementLineParser.PdfParseResult parseResult = pdfStatementLineParser.parse(text, DEFAULT_CURRENCY);
        batch.setTotalRowsDetected(parseResult.drafts().size() + parseResult.errors().size());

        FingerprintService.OccurrenceTracker occurrenceTracker = new FingerprintService.OccurrenceTracker();
        int imported = 0;
        int skippedDuplicate = 0;
        List<RowValidationError> allErrors = new ArrayList<>(parseResult.errors());

        for (NormalizedTransactionDraft draft : parseResult.drafts()) {
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
                        TransactionEnums.ImportSourceType.PDF,
                        draft.transactionDate(), draft.amount(), draft.currency(),
                        TransactionEnums.Direction.valueOf(draft.direction()),
                        draft.merchantRaw(), draft.description(), fingerprint);

                transactionRepository.save(transaction);
                imported++;
            } catch (Exception e) {
                log.error("Error persisting PDF transaction row {} of batch {}", draft.sourceRowNumber(), batch.getId(), e);
                allErrors.add(new RowValidationError(draft.sourceRowNumber(), draft.merchantRaw(),
                        "Internal error saving row: " + e.getMessage()));
            }
        }

        batch.markCompleted(imported, skippedDuplicate, allErrors.size());
        if (!allErrors.isEmpty()) {
            batch.setErrorSummary(allErrors.size() + " row(s) failed — see error detail in response");
        }
        importBatchRepository.save(batch);

        log.info("PDF import batch {} for user {}: {} imported, {} duplicate, {} failed",
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
