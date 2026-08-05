package com.finpilot.importengine.csv;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Parses monetary amounts from CSV text.
 *
 * Handles the real-world noise bank exports contain:
 * - Currency symbols/codes: "₹1,200.50", "INR 1200.50", "$45.00"
 * - Thousands separators: "1,25,000.00" (Indian lakh grouping) and "125,000.00" (Western grouping)
 * - Accounting-style negatives: "(500.00)" means -500.00
 * - Explicit sign: "-500.00" or "+500.00"
 * - Leading/trailing whitespace
 *
 * Returns the ABSOLUTE value — this class only extracts magnitude.
 * Direction (DEBIT/CREDIT) is a separate concern, resolved by
 * {@link CsvRowNormalizer} from either a sign/parenthesis convention or
 * separate debit/credit columns, matching the Transaction contract's rule
 * that amount is always non-negative and direction carries sign meaning.
 */
public final class AmountParser {

    private AmountParser() {
    }

    public record ParsedAmount(BigDecimal absoluteValue, boolean wasNegative) {
    }

    public static Optional<ParsedAmount> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        boolean negative = false;

        // Accounting convention: (500.00) means negative
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            negative = true;
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }

        if (trimmed.startsWith("-")) {
            negative = true;
            trimmed = trimmed.substring(1).trim();
        } else if (trimmed.startsWith("+")) {
            trimmed = trimmed.substring(1).trim();
        }

        // Strip currency symbols and codes, and thousands separators.
        // Keep digits and a single decimal point.
        String cleaned = trimmed
                .replaceAll("[₹$€£]", "")
                .replaceAll("(?i)\\b(INR|USD|EUR|GBP)\\b", "")
                .replace(",", "")
                .trim();

        if (cleaned.isEmpty() || !cleaned.matches("\\d+(\\.\\d+)?")) {
            return Optional.empty();
        }

        try {
            BigDecimal value = new BigDecimal(cleaned);
            return Optional.of(new ParsedAmount(value, negative));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
