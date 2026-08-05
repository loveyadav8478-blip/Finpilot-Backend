package com.finpilot.importengine.csv;

/**
 * A row that failed to parse/validate. Carries enough context (row number,
 * raw content, reason) to show the user exactly what to fix — "row 14
 * failed" alone is useless in a 500-row statement.
 */
public record RowValidationError(
        int sourceRowNumber,
        String rawRowPreview,
        String reason
) {
}
