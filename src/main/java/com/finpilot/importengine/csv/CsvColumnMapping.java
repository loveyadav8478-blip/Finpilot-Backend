package com.finpilot.importengine.csv;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps CSV header names to the semantic fields the normalizer needs.
 *
 * Two real-world column conventions exist and both must be supported:
 * 1. A single signed "amount" column (negative = debit, positive = credit)
 * 2. Separate "debit" and "withdrawal" / "credit" and "deposit" columns
 *    (very common in Indian bank exports), where exactly one is populated
 *    per row and the other is blank/zero.
 *
 * Auto-detection tries common header aliases case-insensitively. If a
 * user's bank uses a header we don't recognize, explicit mapping (passed
 * by the caller, e.g. from a "confirm your columns" UI step) always wins.
 */
public record CsvColumnMapping(
        String dateColumn,
        String descriptionColumn,
        Optional<String> amountColumn,     // signed-amount convention
        Optional<String> debitColumn,      // separate-columns convention
        Optional<String> creditColumn,
        Optional<String> currencyColumn
) {

    private static final List<String> DATE_ALIASES =
            List.of("date", "txn date", "transaction date", "value date", "posting date");
    private static final List<String> DESCRIPTION_ALIASES =
            List.of("description", "narration", "particulars", "details", "remarks");
    private static final List<String> AMOUNT_ALIASES =
            List.of("amount", "transaction amount");
    private static final List<String> DEBIT_ALIASES =
            List.of("debit", "withdrawal", "withdrawal amt", "debit amount");
    private static final List<String> CREDIT_ALIASES =
            List.of("credit", "deposit", "deposit amt", "credit amount");
    private static final List<String> CURRENCY_ALIASES =
            List.of("currency", "ccy");

    /**
     * Attempts to auto-detect a column mapping from a CSV header row.
     * Returns empty if the mandatory columns (date, description, and
     * either amount or debit+credit) can't be found — callers should fall
     * back to asking the user to map columns explicitly rather than
     * guessing further.
     */
    public static Optional<CsvColumnMapping> autoDetect(List<String> headerRow) {
        Map<String, String> normalizedToOriginal = new java.util.LinkedHashMap<>();
        for (String header : headerRow) {
            normalizedToOriginal.put(normalize(header), header);
        }

        Optional<String> date = findFirst(normalizedToOriginal, DATE_ALIASES);
        Optional<String> description = findFirst(normalizedToOriginal, DESCRIPTION_ALIASES);
        Optional<String> amount = findFirst(normalizedToOriginal, AMOUNT_ALIASES);
        Optional<String> debit = findFirst(normalizedToOriginal, DEBIT_ALIASES);
        Optional<String> credit = findFirst(normalizedToOriginal, CREDIT_ALIASES);
        Optional<String> currency = findFirst(normalizedToOriginal, CURRENCY_ALIASES);

        boolean hasAmountConvention = amount.isPresent();
        boolean hasDebitCreditConvention = debit.isPresent() && credit.isPresent();

        if (date.isEmpty() || description.isEmpty() || (!hasAmountConvention && !hasDebitCreditConvention)) {
            return Optional.empty();
        }

        return Optional.of(new CsvColumnMapping(date.get(), description.get(), amount, debit, credit, currency));
    }

    private static Optional<String> findFirst(Map<String, String> normalizedToOriginal, List<String> aliases) {
        for (String alias : aliases) {
            String match = normalizedToOriginal.get(alias);
            if (match != null) {
                return Optional.of(match);
            }
        }
        return Optional.empty();
    }

    private static String normalize(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT);
    }
}
