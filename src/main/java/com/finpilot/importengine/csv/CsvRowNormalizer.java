package com.finpilot.importengine.csv;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns raw parsed CSV rows into normalized drafts (or validation errors),
 * given a resolved column mapping. Pure logic — no Spring, no JPA, no I/O —
 * so it's fully unit testable in isolation.
 *
 * One bad row must never fail the whole batch: each row is validated
 * independently and failures are collected as {@link RowValidationError},
 * not thrown as exceptions. This matches the Phase 2 requirement that
 * partial success (some rows imported, some skipped with reasons) is a
 * first-class outcome, not an edge case.
 */
public final class CsvRowNormalizer {

    private CsvRowNormalizer() {
    }

    public record NormalizationResult(
            List<NormalizedTransactionDraft> drafts,
            List<RowValidationError> errors
    ) {
    }

    /**
     * @param rows            full parsed CSV, including the header row at index 0
     * @param mapping         resolved column mapping (see CsvColumnMapping.autoDetect)
     * @param defaultCurrency used when no currency column is mapped/present
     */
    public static NormalizationResult normalizeAll(List<List<String>> rows, CsvColumnMapping mapping, String defaultCurrency) {
        List<NormalizedTransactionDraft> drafts = new ArrayList<>();
        List<RowValidationError> errors = new ArrayList<>();

        if (rows.isEmpty()) {
            return new NormalizationResult(drafts, errors);
        }

        List<String> header = rows.get(0);
        int dateIdx = header.indexOf(mapping.dateColumn());
        int descIdx = header.indexOf(mapping.descriptionColumn());
        int amountIdx = mapping.amountColumn().map(header::indexOf).orElse(-1);
        int debitIdx = mapping.debitColumn().map(header::indexOf).orElse(-1);
        int creditIdx = mapping.creditColumn().map(header::indexOf).orElse(-1);
        int currencyIdx = mapping.currencyColumn().map(header::indexOf).orElse(-1);

        if (dateIdx < 0 || descIdx < 0 || (amountIdx < 0 && (debitIdx < 0 || creditIdx < 0))) {
            errors.add(new RowValidationError(0, String.join(",", header),
                    "Column mapping does not match this file's header — cannot locate required columns"));
            return new NormalizationResult(drafts, errors);
        }

        for (int rowNum = 1; rowNum < rows.size(); rowNum++) {
            List<String> row = rows.get(rowNum);
            String preview = String.join(",", row);

            if (row.size() <= Math.max(dateIdx, Math.max(descIdx, Math.max(amountIdx, Math.max(debitIdx, creditIdx))))) {
                errors.add(new RowValidationError(rowNum, preview, "Row has fewer columns than the header"));
                continue;
            }

            var dateOpt = DateParser.parse(get(row, dateIdx));
            if (dateOpt.isEmpty()) {
                errors.add(new RowValidationError(rowNum, preview, "Could not parse date: '" + get(row, dateIdx) + "'"));
                continue;
            }

            String description = get(row, descIdx).trim();
            if (description.isEmpty()) {
                errors.add(new RowValidationError(rowNum, preview, "Description is empty"));
                continue;
            }

            AmountResolution amountResolution = resolveAmount(row, amountIdx, debitIdx, creditIdx);
            if (amountResolution.error() != null) {
                errors.add(new RowValidationError(rowNum, preview, amountResolution.error()));
                continue;
            }

            String currency = currencyIdx >= 0 && !get(row, currencyIdx).isBlank()
                    ? get(row, currencyIdx).trim().toUpperCase(java.util.Locale.ROOT)
                    : defaultCurrency;

            drafts.add(new NormalizedTransactionDraft(
                    dateOpt.get(),
                    amountResolution.amount(),
                    amountResolution.direction(),
                    currency,
                    description, // merchantRaw — CSV exports rarely separate merchant from description; the categorization rule engine parses this same field
                    description,
                    rowNum
            ));
        }

        return new NormalizationResult(drafts, errors);
    }

    private record AmountResolution(BigDecimal amount, String direction, String error) {
        static AmountResolution ok(BigDecimal amount, String direction) {
            return new AmountResolution(amount, direction, null);
        }
        static AmountResolution fail(String error) {
            return new AmountResolution(null, null, error);
        }
    }

    private static AmountResolution resolveAmount(List<String> row, int amountIdx, int debitIdx, int creditIdx) {
        if (amountIdx >= 0) {
            var parsed = AmountParser.parse(get(row, amountIdx));
            if (parsed.isEmpty()) {
                return AmountResolution.fail("Could not parse amount: '" + get(row, amountIdx) + "'");
            }
            String direction = parsed.get().wasNegative() ? "DEBIT" : "CREDIT";
            return AmountResolution.ok(parsed.get().absoluteValue(), direction);
        }

        // Separate debit/credit column convention: exactly one should be populated.
        String debitRaw = get(row, debitIdx);
        String creditRaw = get(row, creditIdx);
        var debitParsed = AmountParser.parse(debitRaw);
        var creditParsed = AmountParser.parse(creditRaw);

        boolean hasDebit = debitParsed.isPresent() && debitParsed.get().absoluteValue().compareTo(BigDecimal.ZERO) != 0;
        boolean hasCredit = creditParsed.isPresent() && creditParsed.get().absoluteValue().compareTo(BigDecimal.ZERO) != 0;

        if (hasDebit && hasCredit) {
            return AmountResolution.fail("Both debit and credit columns are populated — ambiguous row");
        }
        if (hasDebit) {
            return AmountResolution.ok(debitParsed.get().absoluteValue(), "DEBIT");
        }
        if (hasCredit) {
            return AmountResolution.ok(creditParsed.get().absoluteValue(), "CREDIT");
        }
        return AmountResolution.fail("Neither debit nor credit column has a value");
    }

    private static String get(List<String> row, int idx) {
        return idx >= 0 && idx < row.size() ? row.get(idx) : "";
    }
}
