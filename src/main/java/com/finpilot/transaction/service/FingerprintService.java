package com.finpilot.transaction.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Computes row-level idempotency fingerprints for imported transactions.
 *
 * Design (see V2__normalized_transaction_contract.sql for full rationale):
 * fingerprint = SHA-256(userId | date | amount | direction | merchant | occurrenceIndex)
 *
 * occurrenceIndex disambiguates genuinely identical transactions within the
 * same batch (e.g. two ₹200 Swiggy orders on the same day) so they don't
 * collide and silently drop one. It's assigned deterministically by calling
 * {@link #assignOccurrenceIndex} once per row, in file order, using a
 * per-batch counter keyed on the same fields used in the hash. Because the
 * counter is deterministic and scoped to a single import run, re-processing
 * the exact same batch (a retried background job) reproduces identical
 * occurrence indices and therefore identical fingerprints — which is what
 * makes the retry safely idempotent instead of a duplicate insert.
 *
 * This class has zero framework dependencies on purpose: fingerprinting is
 * pure, order-sensitive logic that deserves to be unit-tested in isolation
 * from Spring context / database wiring.
 */
public class FingerprintService {

    /**
     * Tracks how many times a given (date, amount, direction, merchant) key
     * has been seen so far within one import run. Must be a fresh instance
     * per batch — never shared or reused across batches/users.
     */
    public static class OccurrenceTracker {
        private final Map<String, Integer> counts = new HashMap<>();

        public int next(LocalDate date, BigDecimal amount, String direction, String merchantNormalized) {
            String key = groupingKey(date, amount, direction, merchantNormalized);
            int index = counts.getOrDefault(key, 0);
            counts.put(key, index + 1);
            return index;
        }

        private static String groupingKey(LocalDate date, BigDecimal amount, String direction, String merchantNormalized) {
            return date + "|" + normalizeAmount(amount) + "|" + direction + "|" + merchantNormalized;
        }
    }

    /**
     * Normalizes merchant text before it enters the fingerprint or the
     * dedup grouping key. Deliberately conservative: strips trailing
     * reference numbers banks append (e.g. "SWIGGY*ORDER 88271" ->
     * "SWIGGY ORDER") so that trivial per-transaction reference-number
     * noise doesn't defeat legitimate duplicate detection, while still
     * treating genuinely different merchants as different.
     */
    public static String normalizeMerchant(String rawMerchant) {
        if (rawMerchant == null) {
            return "";
        }
        String normalized = rawMerchant.toUpperCase(java.util.Locale.ROOT).trim();
        normalized = normalized.replaceAll("[*#]", " ");          // common separators before reference numbers
        normalized = normalized.replaceAll("\\d{4,}", "");         // strip long numeric reference/order IDs
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    private static String normalizeAmount(BigDecimal amount) {
        // Fixed scale so "200" and "200.00" never produce different keys.
        return amount.setScale(4, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    /**
     * Computes the final fingerprint for a single row. Call
     * {@link OccurrenceTracker#next} first, in file order, to get the
     * occurrenceIndex — this method does not manage state itself.
     */
    public String computeFingerprint(UUID userId, LocalDate transactionDate, BigDecimal amount,
                                     String direction, String merchantRaw, int occurrenceIndex) {
        String merchantNormalized = normalizeMerchant(merchantRaw);
        String input = String.join("|",
                userId.toString(),
                transactionDate.toString(),
                normalizeAmount(amount),
                direction,
                merchantNormalized,
                String.valueOf(occurrenceIndex)
        );
        return sha256Hex(input);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM per the Java spec — this is unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
