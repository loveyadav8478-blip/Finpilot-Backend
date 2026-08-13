package com.finpilot.importengine.pdf;

import com.finpilot.importengine.csv.AmountParser;
import com.finpilot.importengine.csv.DateParser;
import com.finpilot.importengine.csv.NormalizedTransactionDraft;
import com.finpilot.importengine.csv.RowValidationError;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw text extracted from PDF bank statements into normalized transactions.
 *
 * Designed to handle common bank statement line layouts:
 * - Single-line transactions: "15/07/2026 SWIGGY ORDER 250.00 Dr"
 * - Separate amount & balance columns: "15-Jul-2026 AMAZON PAY 499.00 15200.50"
 * - Multiline transactions: description text split across consecutive lines before amount
 * - Flexible dates (ISO, DD/MM/YYYY, DD-MMM-YYYY)
 */
public class PdfStatementLineParser {

    private static final Pattern LEADING_DATE = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}[- /][A-Za-z]{3}[- /]\\d{2,4})\\s+(.*)$",
            Pattern.CASE_INSENSITIVE
    );

    // Matches trailing amount with optional Dr/Cr indicator, e.g. "1,500.50 Dr", "5000.00 Cr", "499.00 (Debit)"
    private static final Pattern TRAILING_AMOUNT_WITH_INDICATOR = Pattern.compile(
            "^(.*?)\\s+([₹$]?[+-]?[\\d,]+(?:\\.\\d{1,4})?)\\s*(Dr|DR|Cr|CR|Debit|DEBIT|Credit|CREDIT|\\(Debit\\)|\\(Credit\\))?\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    // Matches trailing amount + running balance, e.g. "499.00 15200.50"
    private static final Pattern TRAILING_AMOUNT_AND_BALANCE = Pattern.compile(
            "^(.*?)\\s+([₹$]?[+-]?[\\d,]+\\.\\d{2})\\s+([₹$]?[\\d,]+\\.\\d{2})\\s*$"
    );

    public record PdfParseResult(
            List<NormalizedTransactionDraft> drafts,
            List<RowValidationError> errors,
            int totalLinesProcessed,
            int skippedHeaderFooterLines
    ) {}

    public PdfParseResult parse(String rawText, String defaultCurrency) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new PdfParseResult(List.of(), List.of(), 0, 0);
        }

        List<NormalizedTransactionDraft> drafts = new ArrayList<>();
        List<RowValidationError> errors = new ArrayList<>();
        String[] lines = rawText.split("\\r?\\n");

        int skipped = 0;
        int lineNo = 0;

        LocalDate pendingDate = null;
        StringBuilder pendingDescription = new StringBuilder();
        int pendingLineNo = 0;

        for (String rawLine : lines) {
            lineNo++;
            String line = rawLine.trim();

            if (line.isEmpty() || isHeaderOrFooter(line)) {
                skipped++;
                continue;
            }

            Matcher dateMatcher = LEADING_DATE.matcher(line);
            if (dateMatcher.matches()) {
                // If we had a previously buffered date that never received an amount, log error or reset
                if (pendingDate != null) {
                    errors.add(new RowValidationError(pendingLineNo, pendingDescription.toString(),
                            "Line started with date but no transaction amount was found before next transaction"));
                }

                String dateText = dateMatcher.group(1);
                String remainder = dateMatcher.group(2).trim();
                Optional<LocalDate> dateOpt = DateParser.parse(dateText);

                if (dateOpt.isEmpty()) {
                    errors.add(new RowValidationError(lineNo, line,
                            "Line looked like it started with a date but could not be parsed: '" + dateText + "'"));
                    pendingDate = null;
                    pendingDescription.setLength(0);
                    continue;
                }

                pendingDate = dateOpt.get();
                pendingDescription.setLength(0);
                pendingDescription.append(remainder);
                pendingLineNo = lineNo;

                // Try parsing transaction on this single line
                if (tryFinalizeTransaction(pendingDate, pendingDescription.toString(), pendingLineNo, defaultCurrency, drafts, errors)) {
                    pendingDate = null;
                    pendingDescription.setLength(0);
                }
            } else if (pendingDate != null) {
                // Continuation line for multi-line description or trailing amount
                pendingDescription.append(" ").append(line);
                if (tryFinalizeTransaction(pendingDate, pendingDescription.toString(), pendingLineNo, defaultCurrency, drafts, errors)) {
                    pendingDate = null;
                    pendingDescription.setLength(0);
                }
            } else {
                skipped++;
            }
        }

        if (pendingDate != null) {
            errors.add(new RowValidationError(pendingLineNo, pendingDescription.toString(),
                    "Incomplete transaction line at end of document without amount"));
        }

        return new PdfParseResult(drafts, errors, lineNo, skipped);
    }

    private boolean tryFinalizeTransaction(LocalDate date, String text, int lineNo, String defaultCurrency,
                                           List<NormalizedTransactionDraft> drafts, List<RowValidationError> errors) {

        // 1. Try matching amount + balance format (e.g. "AMAZON PAY 499.00 15200.50")
        Matcher abMatcher = TRAILING_AMOUNT_AND_BALANCE.matcher(text);
        if (abMatcher.matches()) {
            String merchant = abMatcher.group(1).trim();
            String amountStr = abMatcher.group(2);

            Optional<AmountParser.ParsedAmount> amountOpt = AmountParser.parse(amountStr);
            if (amountOpt.isPresent()) {
                String direction = amountOpt.get().wasNegative() ? "DEBIT" : "DEBIT"; // default DEBIT unless signed
                drafts.add(new NormalizedTransactionDraft(
                        date, amountOpt.get().absoluteValue(), direction, defaultCurrency,
                        merchant.isEmpty() ? "UNKNOWN" : merchant, text, lineNo
                ));
                return true;
            }
        }

        // 2. Try matching amount with indicator format (e.g. "SWIGGY 250.00 Dr" or "SALARY 50000.00 Cr")
        Matcher indMatcher = TRAILING_AMOUNT_WITH_INDICATOR.matcher(text);
        if (indMatcher.matches()) {
            String merchant = indMatcher.group(1).trim();
            String amountStr = indMatcher.group(2);
            String indicator = indMatcher.group(3);

            if (merchant.isEmpty() && indicator == null) {
                return false;
            }

            Optional<AmountParser.ParsedAmount> amountOpt = AmountParser.parse(amountStr);
            if (amountOpt.isPresent()) {
                String direction = resolveDirection(indicator, amountOpt.get().wasNegative());
                drafts.add(new NormalizedTransactionDraft(
                        date, amountOpt.get().absoluteValue(), direction, defaultCurrency,
                        merchant.isEmpty() ? "UNKNOWN" : merchant, text, lineNo
                ));
                return true;
            }
        }

        return false;
    }

    private String resolveDirection(String indicator, boolean wasNegative) {
        if (indicator != null) {
            String ind = indicator.toUpperCase();
            if (ind.contains("CR") || ind.contains("CREDIT")) {
                return "CREDIT";
            }
            if (ind.contains("DR") || ind.contains("DEBIT")) {
                return "DEBIT";
            }
        }
        return wasNegative ? "DEBIT" : "DEBIT"; // default to DEBIT for spend statements
    }

    private boolean isHeaderOrFooter(String line) {
        String lower = line.toLowerCase();
        return lower.startsWith("page ")
                || lower.contains("statement of account")
                || lower.contains("account number")
                || lower.contains("opening balance")
                || lower.contains("closing balance")
                || lower.startsWith("date ") && lower.contains("description");
    }
}
