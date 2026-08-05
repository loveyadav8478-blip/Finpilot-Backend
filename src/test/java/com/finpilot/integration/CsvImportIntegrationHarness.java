//package com.finpilot.integration;
//
//import com.finpilot.importengine.csv.*;
//import com.finpilot.transaction.service.FingerprintService;
//
//import java.io.IOException;
//import java.math.BigDecimal;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.security.MessageDigest;
//import java.sql.*;
//import java.util.List;
//import java.util.UUID;
//
///**
// * Plain-JDBC end-to-end proof that the CSV import pipeline (CsvParser ->
// * CsvColumnMapping -> CsvRowNormalizer -> FingerprintService -> DB insert)
// * works correctly against the real Phase 1 schema.
// *
// * This is NOT a substitute for the real Spring/JPA integration test that
// * should exist once `mvn verify` is runnable — CsvImportService itself
// * (the Spring @Service) is not exercised here. What IS exercised, for
// * real, against a real database: every line of actual import logic this
// * harness reimplements matches CsvImportService's logic exactly, using the
// * same CsvParser / CsvRowNormalizer / FingerprintService classes (not
// * reimplementations) — only the persistence mechanism (raw JDBC instead of
// * Spring Data JPA) differs, because JPA/Hibernate aren't available without
// * Maven in this sandbox.
// */
//public class CsvImportIntegrationHarness {
//
//    private static final String DB_URL = "jdbc:postgresql://localhost:5432/finpilot_dev";
//    private static final String DB_USER = "postgres";
//    private static final String DB_PASSWORD = "postgres";
//
//    private static int passed = 0;
//    private static int failed = 0;
//
//    public static void main(String[] args) throws Exception {
//        Class.forName("org.postgresql.Driver");
//
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
//            conn.setAutoCommit(false);
//
//            UUID userId = createTestUser(conn);
//            String csvContent = Files.readString(
//                    Path.of("src/test/resources/sample-csv/sample_statement.csv"), StandardCharsets.UTF_8);
//            byte[] fileBytes = csvContent.getBytes(StandardCharsets.UTF_8);
//
//            System.out.println("=== First import ===");
//            ImportOutcome first = runImport(conn, userId, "sample_statement.csv", fileBytes);
//            check("6 good rows imported (7 data rows minus 1 unparseable date)", first.imported == 6);
//            check("0 duplicates on first import", first.duplicates == 0);
//            check("1 row failed validation (bad date)", first.failed == 1);
//
//            long txnCount = countTransactions(conn, userId);
//            check("6 transactions actually persisted in the database", txnCount == 6);
//
//            System.out.println("\n=== Re-uploading the SAME file (batch-level dedup) ===");
//            boolean rejected = false;
//            try {
//                runImport(conn, userId, "sample_statement.csv", fileBytes);
//            } catch (DuplicateBatchException e) {
//                rejected = true;
//                System.out.println("Correctly rejected: " + e.getMessage());
//            }
//            check("re-uploading the identical file is rejected at the batch level", rejected);
//
//            long txnCountAfterReupload = countTransactions(conn, userId);
//            check("transaction count unchanged after rejected re-upload", txnCountAfterReupload == 6);
//
//            System.out.println("\n=== Verifying the two same-day/same-amount Swiggy rows both survived ===");
//            long swiggySameDaySameAmountCount = countMatchingRows(conn, userId, "2026-07-01", "450.00");
//            check("both same-day, same-amount Swiggy transactions survived as distinct rows (occurrence-index disambiguation)",
//                    swiggySameDaySameAmountCount == 2);
//
//            conn.commit();
//            cleanupTestUser(conn, userId);
//            conn.commit();
//        }
//
//        System.out.println("\n=== " + passed + " passed, " + failed + " failed ===");
//        if (failed > 0) {
//            System.exit(1);
//        }
//    }
//
//    private record ImportOutcome(int imported, int duplicates, int failed) {
//    }
//
//    private static class DuplicateBatchException extends RuntimeException {
//        DuplicateBatchException(String msg) { super(msg); }
//    }
//
//    /**
//     * Mirrors CsvImportService.importCsv() step for step.
//     */
//    private static ImportOutcome runImport(Connection conn, UUID userId, String filename, byte[] fileBytes) throws Exception {
//        String fileHash = sha256Hex(fileBytes);
//
//        try (PreparedStatement check = conn.prepareStatement(
//                "SELECT id FROM import_batches WHERE user_id = ? AND file_hash = ?")) {
//            check.setObject(1, userId);
//            check.setString(2, fileHash);
//            try (ResultSet rs = check.executeQuery()) {
//                if (rs.next()) {
//                    throw new DuplicateBatchException("File already imported as batch " + rs.getObject(1));
//                }
//            }
//        }
//
//        UUID batchId = UUID.randomUUID();
//        try (PreparedStatement insertBatch = conn.prepareStatement(
//                "INSERT INTO import_batches (id, user_id, source_type, file_hash, original_filename, status) VALUES (?, ?, 'CSV', ?, ?, 'PROCESSING')")) {
//            insertBatch.setObject(1, batchId);
//            insertBatch.setObject(2, userId);
//            insertBatch.setString(3, fileHash);
//            insertBatch.setString(4, filename);
//            insertBatch.executeUpdate();
//        }
//
//        String content = new String(fileBytes, StandardCharsets.UTF_8);
//        List<List<String>> rows = CsvParser.parse(content);
//        CsvColumnMapping mapping = CsvColumnMapping.autoDetect(rows.get(0)).orElseThrow();
//        CsvRowNormalizer.NormalizationResult normalized = CsvRowNormalizer.normalizeAll(rows, mapping, "INR");
//
//        FingerprintService fingerprintService = new FingerprintService();
//        FingerprintService.OccurrenceTracker tracker = new FingerprintService.OccurrenceTracker();
//
//        int imported = 0;
//        int duplicates = 0;
//
//        for (NormalizedTransactionDraft draft : normalized.drafts()) {
//            int occurrenceIndex = tracker.next(draft.transactionDate(), draft.amount(), draft.direction(),
//                    FingerprintService.normalizeMerchant(draft.merchantRaw()));
//            String fingerprint = fingerprintService.computeFingerprint(
//                    userId, draft.transactionDate(), draft.amount(), draft.direction(),
//                    draft.merchantRaw(), occurrenceIndex);
//
//            try (PreparedStatement dupCheck = conn.prepareStatement(
//                    "SELECT id FROM transactions WHERE user_id = ? AND fingerprint = ?")) {
//                dupCheck.setObject(1, userId);
//                dupCheck.setString(2, fingerprint);
//                try (ResultSet rs = dupCheck.executeQuery()) {
//                    if (rs.next()) {
//                        duplicates++;
//                        continue;
//                    }
//                }
//            }
//
//            try (PreparedStatement insertTxn = conn.prepareStatement(
//                    "INSERT INTO transactions (user_id, import_batch_id, source_type, transaction_date, amount, currency, direction, merchant_raw, description, fingerprint) "
//                            + "VALUES (?, ?, 'CSV', ?, ?, ?, ?::transaction_direction, ?, ?, ?)")) {
//                insertTxn.setObject(1, userId);
//                insertTxn.setObject(2, batchId);
//                insertTxn.setObject(3, draft.transactionDate());
//                insertTxn.setBigDecimal(4, draft.amount());
//                insertTxn.setString(5, draft.currency());
//                insertTxn.setString(6, draft.direction());
//                insertTxn.setString(7, draft.merchantRaw());
//                insertTxn.setString(8, draft.description());
//                insertTxn.setString(9, fingerprint);
//                insertTxn.executeUpdate();
//                imported++;
//            }
//        }
//
//        try (PreparedStatement updateBatch = conn.prepareStatement(
//                "UPDATE import_batches SET status = 'COMPLETED', rows_imported = ?, rows_skipped_duplicate = ?, rows_failed = ?, total_rows_detected = ? WHERE id = ?")) {
//            updateBatch.setInt(1, imported);
//            updateBatch.setInt(2, duplicates);
//            updateBatch.setInt(3, normalized.errors().size());
//            updateBatch.setInt(4, rows.size() - 1);
//            updateBatch.setObject(5, batchId);
//            updateBatch.executeUpdate();
//        }
//
//        conn.commit();
//        return new ImportOutcome(imported, duplicates, normalized.errors().size());
//    }
//
//    private static UUID createTestUser(Connection conn) throws SQLException {
//        UUID userId = UUID.randomUUID();
//        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO users (id, email) VALUES (?, ?)")) {
//            ps.setObject(1, userId);
//            ps.setString(2, "harness-" + userId + "@finpilot.dev");
//            ps.executeUpdate();
//        }
//        conn.commit();
//        return userId;
//    }
//
//    private static void cleanupTestUser(Connection conn, UUID userId) throws SQLException {
//        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
//            ps.setObject(1, userId);
//            ps.executeUpdate();
//        }
//    }
//
//    private static long countTransactions(Connection conn, UUID userId) throws SQLException {
//        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM transactions WHERE user_id = ?")) {
//            ps.setObject(1, userId);
//            try (ResultSet rs = ps.executeQuery()) {
//                rs.next();
//                return rs.getLong(1);
//            }
//        }
//    }
//
//    private static long countMatchingRows(Connection conn, UUID userId, String date, String amount) throws SQLException {
//        try (PreparedStatement ps = conn.prepareStatement(
//                "SELECT count(*) FROM transactions WHERE user_id = ? AND transaction_date = ?::date AND amount = ?::numeric")) {
//            ps.setObject(1, userId);
//            ps.setString(2, date);
//            ps.setString(3, amount);
//            try (ResultSet rs = ps.executeQuery()) {
//                rs.next();
//                return rs.getLong(1);
//            }
//        }
//    }
//
//    private static String sha256Hex(byte[] input) throws Exception {
//        MessageDigest digest = MessageDigest.getInstance("SHA-256");
//        byte[] hash = digest.digest(input);
//        StringBuilder hex = new StringBuilder(hash.length * 2);
//        for (byte b : hash) {
//            String h = Integer.toHexString(0xff & b);
//            if (h.length() == 1) hex.append('0');
//            hex.append(h);
//        }
//        return hex.toString();
//    }
//
//    private static void check(String description, boolean condition) {
//        if (condition) {
//            passed++;
//            System.out.println("PASS: " + description);
//        } else {
//            failed++;
//            System.out.println("FAIL: " + description);
//        }
//    }
//}
