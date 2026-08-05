package com.finpilot.importengine.csv;

import java.util.ArrayList;
import java.util.List;

/**
 * A small RFC4180-compliant CSV parser, written dependency-free so it can
 * be compiled and unit tested without pulling in a CSV library or Spring.
 *
 * Handles the cases that break naive {@code line.split(",")} parsing, which
 * matters because real bank-exported CSVs routinely hit these:
 * - Quoted fields containing commas (e.g. a description like "Payment, ref 123")
 * - Escaped quotes within quoted fields ("" -> ")
 * - Quoted fields containing embedded newlines (multi-line descriptions)
 * - A UTF-8 byte-order-mark at the start of the file (common from Excel exports)
 * - Blank lines interspersed in the file (skipped, not treated as empty rows)
 */
public final class CsvParser {

    private CsvParser() {
    }

    public static List<List<String>> parse(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        // Strip UTF-8 BOM if present — Excel commonly prepends this.
        if (content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }

        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();

        boolean inQuotes = false;
        boolean rowHasContent = false;

        int i = 0;
        int len = content.length();

        while (i < len) {
            char c = content.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && content.charAt(i + 1) == '"') {
                        field.append('"'); // escaped quote
                        i += 2;
                        continue;
                    } else {
                        inQuotes = false;
                        i++;
                        continue;
                    }
                } else {
                    field.append(c);
                    i++;
                    continue;
                }
            }

            switch (c) {
                case '"' -> {
                    inQuotes = true;
                    rowHasContent = true;
                    i++;
                }
                case ',' -> {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    rowHasContent = true;
                    i++;
                }
                case '\r' -> {
                    currentRow.add(field.toString());
                    field.setLength(0);

                    if (rowHasContent) {
                        rows.add(currentRow);
                    }

                    currentRow = new ArrayList<>();
                    rowHasContent = false;

                    // Skip '\n' if this is CRLF
                    if (i + 1 < len && content.charAt(i + 1) == '\n') {
                        i++;
                    }

                    i++;
                }// ignore, \n (or EOF) ends the row
                case '\n' -> {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    if (rowHasContent) {
                        rows.add(currentRow);
                    }
                    currentRow = new ArrayList<>();
                    rowHasContent = false;
                    i++;
                }
                default -> {
                    field.append(c);
                    rowHasContent = true;
                    i++;
                }
            }
        }

        // Final field/row if the file doesn't end with a newline
        if (field.length() > 0 || rowHasContent) {
            currentRow.add(field.toString());
            rows.add(currentRow);
        }

        return rows;
    }
}
