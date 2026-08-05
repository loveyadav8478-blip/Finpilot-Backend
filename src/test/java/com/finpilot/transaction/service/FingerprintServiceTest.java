//package com.finpilot.transaction.service;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.util.UUID;
//
///**
// * Dependency-free test runner for FingerprintService. Not the final test
// * form (that will be a proper JUnit test once Maven/Spring wiring exists),
// * but lets us verify the actual algorithm behavior right now rather than
// * just asserting it works by inspection.
// */
//public class FingerprintServiceTest {
//
//    private static int passed = 0;
//    private static int failed = 0;
//
//    public static void main(String[] args) {
//        testMerchantNormalizationStripsReferenceNumbers();
//        testMerchantNormalizationPreservesDistinctMerchants();
//        testIdenticalTransactionsGetDifferentFingerprints();
//        testRetryOfSameBatchReproducesIdenticalFingerprints();
//        testDifferentUsersNeverCollide();
//        testDifferentAmountsNeverCollide();
//        testOccurrenceTrackerIsOrderSensitiveNotJustCount();
//
//        System.out.println("\n=== " + passed + " passed, " + failed + " failed ===");
//        if (failed > 0) {
//            System.exit(1);
//        }
//    }
//
//    private static void testMerchantNormalizationStripsReferenceNumbers() {
//        String a = FingerprintService.normalizeMerchant("SWIGGY*ORDER 88271");
//        String b = FingerprintService.normalizeMerchant("SWIGGY*ORDER 99342");
//        check("merchant normalization strips reference numbers so repeat orders group together",
//                a.equals(b) && a.equals("SWIGGY ORDER"));
//    }
//
//    private static void testMerchantNormalizationPreservesDistinctMerchants() {
//        String swiggy = FingerprintService.normalizeMerchant("SWIGGY*ORDER 88271");
//        String uber = FingerprintService.normalizeMerchant("UBER TRIP HELP.UBER.COM");
//        check("distinct merchants remain distinct after normalization", !swiggy.equals(uber));
//    }
//
//    private static void testIdenticalTransactionsGetDifferentFingerprints() {
//        FingerprintService svc = new FingerprintService();
//        FingerprintService.OccurrenceTracker tracker = new FingerprintService.OccurrenceTracker();
//        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
//        LocalDate date = LocalDate.of(2026, 7, 1);
//        BigDecimal amount = new BigDecimal("200.00");
//
//        // Two identical-looking Swiggy orders on the same day, same amount.
//        int idx1 = tracker.next(date, amount, "DEBIT", FingerprintService.normalizeMerchant("SWIGGY*ORDER 88271"));
//        int idx2 = tracker.next(date, amount, "DEBIT", FingerprintService.normalizeMerchant("SWIGGY*ORDER 99342"));
//
//        String fp1 = svc.computeFingerprint(userId, date, amount, "DEBIT", "SWIGGY*ORDER 88271", idx1);
//        String fp2 = svc.computeFingerprint(userId, date, amount, "DEBIT", "SWIGGY*ORDER 99342", idx2);
//
//        check("occurrence indices differ for repeat same-day/same-amount transactions", idx1 == 0 && idx2 == 1);
//        check("identical transactions produce DIFFERENT fingerprints (both survive dedup)", !fp1.equals(fp2));
//        check("fingerprint is a 64-char hex SHA-256 digest", fp1.length() == 64 && fp1.matches("[0-9a-f]+"));
//    }
//
//    private static void testRetryOfSameBatchReproducesIdenticalFingerprints() {
//        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
//        LocalDate date = LocalDate.of(2026, 7, 1);
//        BigDecimal amount = new BigDecimal("200.00");
//        FingerprintService svc = new FingerprintService();
//
//        // First "processing" of the batch
//        FingerprintService.OccurrenceTracker tracker1 = new FingerprintService.OccurrenceTracker();
//        int idxA1 = tracker1.next(date, amount, "DEBIT", FingerprintService.normalizeMerchant("SWIGGY*ORDER 88271"));
//        int idxA2 = tracker1.next(date, amount, "DEBIT", FingerprintService.normalizeMerchant("SWIGGY*ORDER 99342"));
//        String fpA1 = svc.computeFingerprint(userId, date, amount, "DEBIT", "SWIGGY*ORDER 88271", idxA1);
//        String fpA2 = svc.computeFingerprint(userId, date, amount, "DEBIT", "SWIGGY*ORDER 99342", idxA2);
//
//        // Simulated RETRY: same batch, same file, processed again from scratch
//        // with a fresh tracker (as would happen if a worker crashes and
//        // re-runs the whole batch).
//        FingerprintService.OccurrenceTracker tracker2 = new FingerprintService.OccurrenceTracker();
//        int idxB1 = tracker2.next(date, amount, "DEBIT", FingerprintService.normalizeMerchant("SWIGGY*ORDER 88271"));
//        int idxB2 = tracker2.next(date, amount, "DEBIT", FingerprintService.normalizeMerchant("SWIGGY*ORDER 99342"));
//        String fpB1 = svc.computeFingerprint(userId, date, amount, "DEBIT", "SWIGGY*ORDER 88271", idxB1);
//        String fpB2 = svc.computeFingerprint(userId, date, amount, "DEBIT", "SWIGGY*ORDER 99342", idxB2);
//
//        check("retrying the same batch reproduces IDENTICAL fingerprints (safe idempotent retry)",
//                fpA1.equals(fpB1) && fpA2.equals(fpB2));
//    }
//
//    private static void testDifferentUsersNeverCollide() {
//        FingerprintService svc = new FingerprintService();
//        LocalDate date = LocalDate.of(2026, 7, 1);
//        BigDecimal amount = new BigDecimal("200.00");
//
//        String fpUser1 = svc.computeFingerprint(
//                UUID.fromString("11111111-1111-1111-1111-111111111111"),
//                date, amount, "DEBIT", "SWIGGY*ORDER 88271", 0);
//        String fpUser2 = svc.computeFingerprint(
//                UUID.fromString("22222222-2222-2222-2222-222222222222"),
//                date, amount, "DEBIT", "SWIGGY*ORDER 88271", 0);
//
//        check("identical transaction for two different users produces different fingerprints",
//                !fpUser1.equals(fpUser2));
//    }
//
//    private static void testDifferentAmountsNeverCollide() {
//        FingerprintService svc = new FingerprintService();
//        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
//        LocalDate date = LocalDate.of(2026, 7, 1);
//
//        String fp200 = svc.computeFingerprint(userId, date, new BigDecimal("200.00"), "DEBIT", "SWIGGY*ORDER 88271", 0);
//        String fp20000 = svc.computeFingerprint(userId, date, new BigDecimal("200.0000"), "DEBIT", "SWIGGY*ORDER 88271", 0);
//        String fpDifferent = svc.computeFingerprint(userId, date, new BigDecimal("250.00"), "DEBIT", "SWIGGY*ORDER 88271", 0);
//
//        check("amounts differing only in trailing zero scale produce the SAME fingerprint (200 == 200.0000)",
//                fp200.equals(fp20000));
//        check("genuinely different amounts produce different fingerprints", !fp200.equals(fpDifferent));
//    }
//
//    private static void testOccurrenceTrackerIsOrderSensitiveNotJustCount() {
//        FingerprintService.OccurrenceTracker tracker = new FingerprintService.OccurrenceTracker();
//        LocalDate date = LocalDate.of(2026, 7, 1);
//        BigDecimal amount = new BigDecimal("200.00");
//
//        int swiggyIdx = tracker.next(date, amount, "DEBIT", "SWIGGY ORDER");
//        int uberIdx = tracker.next(date, amount, "DEBIT", "UBER TRIP"); // different merchant, same date/amount
//        int swiggyIdx2 = tracker.next(date, amount, "DEBIT", "SWIGGY ORDER"); // second swiggy order
//
//        check("different merchants don't share an occurrence counter even with same date/amount",
//                swiggyIdx == 0 && uberIdx == 0 && swiggyIdx2 == 1);
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
