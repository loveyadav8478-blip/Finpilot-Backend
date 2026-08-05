//package com.finpilot.importengine.csv;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.util.List;
//import java.util.Optional;
//
//public class CsvImportPipelineTest {
//
//    private static int passed = 0;
//    private static int failed = 0;
//
//    public static void main(String[] args) {
//        // --- CsvParser ---
//        testParsesSimpleCsv();
//        testParsesQuotedFieldWithEmbeddedComma();
//        testParsesEscapedQuotesWithinField();
//        testParsesQuotedFieldWithEmbeddedNewline();
//        testStripsUtf8Bom();
//        testSkipsBlankLines();
//
//        // --- DateParser ---
//        testDateParserIsoFormat();
//        testDateParserDdMmYyyy();
//        testDateParserDdMmmYyyy();
//        testDateParserRejectsGarbage();
//
//        // --- AmountParser ---
//        testAmountParserPlain();
//        testAmountParserWithCurrencySymbolAndCommas();
//        testAmountParserParenthesesNegative();
//        testAmountParserExplicitNegative();
//        testAmountParserRejectsGarbage();
//
//        // --- CsvColumnMapping auto-detection ---
//        testAutoDetectSignedAmountConvention();
//        testAutoDetectDebitCreditConvention();
//        testAutoDetectFailsWithoutRequiredColumns();
//
//        // --- CsvRowNormalizer: end-to-end ---
//        testNormalizeSignedAmountCsvEndToEnd();
//        testNormalizeDebitCreditCsvEndToEnd();
//        testNormalizePartialFailureDoesNotAbortWholeBatch();
//        testNormalizeBothDebitAndCreditPopulatedIsError();
//
//        System.out.println("\n=== " + passed + " passed, " + failed + " failed ===");
//        if (failed > 0) {
//            System.exit(1);
//        }
//    }
//
//    // ---------------- CsvParser ----------------
//
//    private static void testParsesSimpleCsv() {
//        List<List<String>> rows = CsvParser.parse("date,description,amount\n2026-07-01,Swiggy,200.00\n");
//        check("simple CSV parses into 2 rows", rows.size() == 2);
//        check("header row has 3 fields", rows.get(0).size() == 3);
//        check("data row values correct", rows.get(1).equals(List.of("2026-07-01", "Swiggy", "200.00")));
//    }
//
//    private static void testParsesQuotedFieldWithEmbeddedComma() {
//        List<List<String>> rows = CsvParser.parse("date,description,amount\n2026-07-01,\"Payment, ref 123\",200.00\n");
//        check("quoted field with embedded comma stays one field", rows.get(1).get(1).equals("Payment, ref 123"));
//    }
//
//    private static void testParsesEscapedQuotesWithinField() {
//        List<List<String>> rows = CsvParser.parse("date,description,amount\n2026-07-01,\"Say \"\"hello\"\"\",200.00\n");
//        check("escaped double-quotes unescape correctly", rows.get(1).get(1).equals("Say \"hello\""));
//    }
//
//    private static void testParsesQuotedFieldWithEmbeddedNewline() {
//        List<List<String>> rows = CsvParser.parse("date,description,amount\n2026-07-01,\"Line1\nLine2\",200.00\n");
//        check("exactly 2 rows despite embedded newline in quoted field", rows.size() == 2);
//        check("embedded newline preserved within the field", rows.get(1).get(1).equals("Line1\nLine2"));
//    }
//
//    private static void testStripsUtf8Bom() {
//        String withBom = "\uFEFFdate,description,amount\n2026-07-01,Swiggy,200.00\n";
//        List<List<String>> rows = CsvParser.parse(withBom);
//        check("BOM stripped, header starts clean", rows.get(0).get(0).equals("date"));
//    }
//
//    private static void testSkipsBlankLines() {
//        List<List<String>> rows = CsvParser.parse("date,description,amount\n\n2026-07-01,Swiggy,200.00\n\n");
//        check("blank lines are skipped, not treated as empty rows", rows.size() == 2);
//    }
//
//    // ---------------- DateParser ----------------
//
//    private static void testDateParserIsoFormat() {
//        Optional<LocalDate> d = DateParser.parse("2026-07-01");
//        check("ISO date parses", d.isPresent() && d.get().equals(LocalDate.of(2026, 7, 1)));
//    }
//
//    private static void testDateParserDdMmYyyy() {
//        Optional<LocalDate> d = DateParser.parse("01/07/2026");
//        check("dd/MM/yyyy parses as 1 July 2026 (India convention default)", d.isPresent() && d.get().equals(LocalDate.of(2026, 7, 1)));
//    }
//
//    private static void testDateParserDdMmmYyyy() {
//        Optional<LocalDate> d = DateParser.parse("01-Jul-2026");
//        check("dd-MMM-yyyy parses", d.isPresent() && d.get().equals(LocalDate.of(2026, 7, 1)));
//    }
//
//    private static void testDateParserRejectsGarbage() {
//        Optional<LocalDate> d = DateParser.parse("not a date");
//        check("garbage date string returns empty, not an exception", d.isEmpty());
//    }
//
//    // ---------------- AmountParser ----------------
//
//    private static void testAmountParserPlain() {
//        var p = AmountParser.parse("200.00");
//        check("plain amount parses", p.isPresent() && p.get().absoluteValue().compareTo(new BigDecimal("200.00")) == 0);
//        check("plain positive amount is not negative", !p.get().wasNegative());
//    }
//
//    private static void testAmountParserWithCurrencySymbolAndCommas() {
//        var p = AmountParser.parse("₹1,25,000.50");
//        check("currency symbol and Indian comma grouping stripped correctly",
//                p.isPresent() && p.get().absoluteValue().compareTo(new BigDecimal("125000.50")) == 0);
//    }
//
//    private static void testAmountParserParenthesesNegative() {
//        var p = AmountParser.parse("(500.00)");
//        check("parentheses convention parses as negative", p.isPresent() && p.get().wasNegative());
//        check("parentheses convention keeps absolute value positive", p.get().absoluteValue().compareTo(new BigDecimal("500.00")) == 0);
//    }
//
//    private static void testAmountParserExplicitNegative() {
//        var p = AmountParser.parse("-500.00");
//        check("explicit minus sign parses as negative", p.isPresent() && p.get().wasNegative());
//    }
//
//    private static void testAmountParserRejectsGarbage() {
//        var p = AmountParser.parse("N/A");
//        check("garbage amount string returns empty, not an exception", p.isEmpty());
//    }
//
//    // ---------------- CsvColumnMapping ----------------
//
//    private static void testAutoDetectSignedAmountConvention() {
//        var mapping = CsvColumnMapping.autoDetect(List.of("Date", "Narration", "Amount"));
//        check("auto-detects signed-amount convention", mapping.isPresent() && mapping.get().amountColumn().isPresent());
//    }
//
//    private static void testAutoDetectDebitCreditConvention() {
//        var mapping = CsvColumnMapping.autoDetect(List.of("Value Date", "Particulars", "Withdrawal Amt", "Deposit Amt"));
//        check("auto-detects debit/credit convention",
//                mapping.isPresent() && mapping.get().debitColumn().isPresent() && mapping.get().creditColumn().isPresent());
//    }
//
//    private static void testAutoDetectFailsWithoutRequiredColumns() {
//        var mapping = CsvColumnMapping.autoDetect(List.of("Foo", "Bar"));
//        check("auto-detect correctly fails (returns empty) on an unrecognized header", mapping.isEmpty());
//    }
//
//    // ---------------- CsvRowNormalizer end-to-end ----------------
//
//    private static void testNormalizeSignedAmountCsvEndToEnd() {
//        String csv = "Date,Narration,Amount\n2026-07-01,SWIGGY*ORDER 88271,-200.00\n2026-07-02,Salary Credit,50000.00\n";
//        List<List<String>> rows = CsvParser.parse(csv);
//        var mapping = CsvColumnMapping.autoDetect(rows.get(0)).orElseThrow();
//        var result = CsvRowNormalizer.normalizeAll(rows, mapping, "INR");
//
//        check("2 drafts produced, 0 errors", result.drafts().size() == 2 && result.errors().isEmpty());
//        check("negative amount correctly resolves to DEBIT", result.drafts().get(0).direction().equals("DEBIT"));
//        check("DEBIT amount stored as absolute (non-negative)", result.drafts().get(0).amount().compareTo(new BigDecimal("200.00")) == 0);
//        check("positive amount correctly resolves to CREDIT", result.drafts().get(1).direction().equals("CREDIT"));
//    }
//
//    private static void testNormalizeDebitCreditCsvEndToEnd() {
//        String csv = "Value Date,Particulars,Withdrawal Amt,Deposit Amt\n"
//                + "01/07/2026,SWIGGY ORDER,200.00,\n"
//                + "02/07/2026,Salary,,50000.00\n";
//        List<List<String>> rows = CsvParser.parse(csv);
//        var mapping = CsvColumnMapping.autoDetect(rows.get(0)).orElseThrow();
//        var result = CsvRowNormalizer.normalizeAll(rows, mapping, "INR");
//
//        check("debit/credit convention produces 2 drafts, 0 errors", result.drafts().size() == 2 && result.errors().isEmpty());
//        check("populated debit column resolves to DEBIT", result.drafts().get(0).direction().equals("DEBIT"));
//        check("populated credit column resolves to CREDIT", result.drafts().get(1).direction().equals("CREDIT"));
//    }
//
//    private static void testNormalizePartialFailureDoesNotAbortWholeBatch() {
//        String csv = "Date,Narration,Amount\n"
//                + "2026-07-01,Good Row,200.00\n"
//                + "not-a-date,Bad Row,200.00\n"
//                + "2026-07-03,Another Good Row,300.00\n";
//        List<List<String>> rows = CsvParser.parse(csv);
//        var mapping = CsvColumnMapping.autoDetect(rows.get(0)).orElseThrow();
//        var result = CsvRowNormalizer.normalizeAll(rows, mapping, "INR");
//
//        check("2 good rows still import despite 1 bad row", result.drafts().size() == 2);
//        check("1 error captured with the right row number", result.errors().size() == 1 && result.errors().get(0).sourceRowNumber() == 2);
//    }
//
//    private static void testNormalizeBothDebitAndCreditPopulatedIsError() {
//        String csv = "Date,Narration,Withdrawal Amt,Deposit Amt\n2026-07-01,Weird Row,200.00,300.00\n";
//        List<List<String>> rows = CsvParser.parse(csv);
//        var mapping = CsvColumnMapping.autoDetect(rows.get(0)).orElseThrow();
//        var result = CsvRowNormalizer.normalizeAll(rows, mapping, "INR");
//
//        check("ambiguous row (both debit and credit populated) is rejected as an error, not guessed",
//                result.drafts().isEmpty() && result.errors().size() == 1);
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
