package com.finpilot.importengine.csv;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single successfully-parsed and normalized CSV row, ready to be turned
 * into a Transaction entity by the Spring-layer import service. Kept as a
 * plain record (no JPA) so the normalization logic itself stays unit
 * testable without a database or Spring context.
 */
public record NormalizedTransactionDraft(
        LocalDate transactionDate,
        BigDecimal amount,        // always non-negative — see AmountParser
        String direction,          // "DEBIT" or "CREDIT"
        String currency,
        String merchantRaw,
        String description,
        int sourceRowNumber
) {
}
