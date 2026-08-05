package com.finpilot.importengine.csv;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Parses transaction dates from CSV text.
 *
 * Real bank statement exports use inconsistent date formats — this tries a
 * fixed, ordered list of common ones. Ordering matters for ambiguous cases
 * like "01/02/2026": we default to day/month/year (common in Indian bank
 * exports, which is FinPilot's primary target market per the product
 * spec) before falling back to month/day/year. This is a real ambiguity
 * that CANNOT be resolved with certainty from the string alone — if a
 * specific bank's format is known in advance, prefer passing an explicit
 * formatter via {@link #parse(String, DateTimeFormatter)} instead of
 * relying on this best-effort fallback chain.
 */
public final class DateParser {

    private DateParser() {
    }

    private static final List<DateTimeFormatter> FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),   // ISO — unambiguous, tried first
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),    // India/UK convention (default assumption)
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),    // US convention
            DateTimeFormatter.ofPattern("dd MMM yyyy"),   // "01 Jul 2026"
            DateTimeFormatter.ofPattern("dd-MMM-yyyy"),   // "01-Jul-2026" — common in Indian bank statements
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    public static Optional<LocalDate> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        for (DateTimeFormatter formatter : FORMATS) {
            try {
                return Optional.of(LocalDate.parse(trimmed, formatter));
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return Optional.empty();
    }

    public static Optional<LocalDate> parse(String raw, DateTimeFormatter explicitFormat) {
        if (raw == null || raw.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(raw.trim(), explicitFormat));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
