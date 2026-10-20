package com.omsai.coaching.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal CSV writer (RFC 4180 style) used by the export features.
 *
 * <p>Rules implemented here:</p>
 * <ul>
 *   <li>UTF-8 text, one header row, comma separated, one record per
 *       line;</li>
 *   <li>a field is quoted when it contains a comma, a double quote or a
 *       line break, and embedded quotes are doubled;</li>
 *   <li>a field that starts with a character a spreadsheet would treat
 *       as the start of a formula ({@code =}, {@code +}, {@code -},
 *       {@code @}, TAB, CR) is prefixed with an apostrophe so it is
 *       imported as text and never evaluated. Ordinary names, numbers
 *       and dates are written unchanged;</li>
 *   <li>{@code null} values become empty fields - never the text
 *       "null" and never a zero.</li>
 * </ul>
 *
 * <p>Deliberately dependency-free: the project has no CSV library and
 * does not need one.</p>
 */
public final class CsvWriter {

    private static final char SEPARATOR = ',';
    private static final char QUOTE = '"';
    private static final String LINE_SEPARATOR = "\n";

    /** Characters a spreadsheet interprets as the start of a formula. */
    private static final String FORMULA_PREFIXES = "=+-@\t\r";

    private final List<String> records = new ArrayList<>();

    private CsvWriter() {
    }

    /** A new, empty writer. */
    public static CsvWriter create() {
        return new CsvWriter();
    }

    /** Writes the header row. */
    public CsvWriter header(String... columns) {
        return row(columns);
    }

    /** Writes one record. */
    public CsvWriter row(String... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(SEPARATOR);
            }
            line.append(escape(values[i]));
        }
        records.add(line.toString());
        return this;
    }

    /** Writes one record from a list of values. */
    public CsvWriter row(List<String> values) {
        return row(values.toArray(new String[0]));
    }

    /** The finished CSV document (UTF-8 text, trailing newline). */
    public String toCsv() {
        if (records.isEmpty()) {
            return "";
        }
        return String.join(LINE_SEPARATOR, records) + LINE_SEPARATOR;
    }

    /**
     * Escapes one field: neutralises spreadsheet formulas, quotes when
     * needed and doubles embedded quotes.
     */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        String text = value;
        if (!text.isEmpty() && FORMULA_PREFIXES.indexOf(text.charAt(0)) >= 0) {
            // Neutralise formula injection; the apostrophe marks the cell
            // as text in Excel/LibreOffice and is not shown to the user.
            text = "'" + text;
        }
        if (needsQuoting(text)) {
            return QUOTE + text.replace(String.valueOf(QUOTE), "\"\"") + QUOTE;
        }
        return text;
    }

    private static boolean needsQuoting(String text) {
        return text.indexOf(SEPARATOR) >= 0
                || text.indexOf(QUOTE) >= 0
                || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0;
    }
}
