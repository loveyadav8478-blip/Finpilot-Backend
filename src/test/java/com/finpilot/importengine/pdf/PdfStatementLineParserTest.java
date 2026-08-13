//package com.finpilot.importengine.pdf;
//
//import com.finpilot.importengine.csv.NormalizedTransactionDraft;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class PdfStatementLineParserTest {
//
//    private PdfStatementLineParser parser;
//
//    @BeforeEach
//    void setUp() {
//        parser = new PdfStatementLineParser();
//    }
//
//    @Test
//    void parseSingleLineTransactionsWithDrCr() {
//        String pdfText = """
//                Statement of Account
//                Page 1 of 2
//                15/07/2026 SWIGGY ORDER 88271 250.50 Dr
//                16-Jul-2026 SALARY CREDIT CORP 75000.00 Cr
//                2026-07-18 UBER TRIP 350.00 DEBIT
//                """;
//
//        PdfStatementLineParser.PdfParseResult result = parser.parse(pdfText, "INR");
//
//        assertEquals(3, result.drafts().size());
//        assertTrue(result.errors().isEmpty());
//
//        NormalizedTransactionDraft row1 = result.drafts().get(0);
//        assertEquals(LocalDate.of(2026, 7, 15), row1.transactionDate());
//        assertEquals(new BigDecimal("250.50"), row1.amount());
//        assertEquals("DEBIT", row1.direction());
//        assertEquals("SWIGGY ORDER 88271", row1.merchantRaw());
//
//        NormalizedTransactionDraft row2 = result.drafts().get(1);
//        assertEquals(LocalDate.of(2026, 7, 16), row2.transactionDate());
//        assertEquals(new BigDecimal("75000.00"), row2.amount());
//        assertEquals("CREDIT", row2.direction());
//        assertEquals("SALARY CREDIT CORP", row2.merchantRaw());
//    }
//
//    @Test
//    void parseAmountAndBalanceFormat() {
//        String pdfText = """
//                01-08-2026 AMAZON PAY INDIA 499.00 15200.50
//                02-08-2026 ZOMATO RESTAURANT 320.00 14880.50
//                """;
//
//        PdfStatementLineParser.PdfParseResult result = parser.parse(pdfText, "INR");
//
//        assertEquals(2, result.drafts().size());
//        assertEquals(new BigDecimal("499.00"), result.drafts().get(0).amount());
//        assertEquals("AMAZON PAY INDIA", result.drafts().get(0).merchantRaw());
//        assertEquals("DEBIT", result.drafts().get(0).direction());
//    }
//
//    @Test
//    void parseMultiLineDescription() {
//        String pdfText = """
//                10/08/2026 NETFLIX ENTERTAINMENT
//                SUBSCRIPTION BILLING 799.00 Dr
//                """;
//
//        PdfStatementLineParser.PdfParseResult result = parser.parse(pdfText, "INR");
//
//        assertEquals(1, result.drafts().size());
//        assertEquals(LocalDate.of(2026, 8, 10), result.drafts().get(0).transactionDate());
//        assertEquals(new BigDecimal("799.00"), result.drafts().get(0).amount());
//        assertTrue(result.drafts().get(0).merchantRaw().contains("NETFLIX ENTERTAINMENT SUBSCRIPTION BILLING"));
//    }
//
//    @Test
//    void recordErrorForIncompleteTransaction() {
//        String pdfText = """
//                10/08/2026 UNFINISHED TRANSACTION WITHOUT AMOUNT
//                12/08/2026 VALID TRANSACTION 100.00 Dr
//                """;
//
//        PdfStatementLineParser.PdfParseResult result = parser.parse(pdfText, "INR");
//
//        assertEquals(1, result.drafts().size());
//        assertEquals(1, result.errors().size());
//        assertTrue(result.errors().get(0).errorMessage().contains("no transaction amount was found"));
//    }
//}
