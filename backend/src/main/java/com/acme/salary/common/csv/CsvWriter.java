package com.acme.salary.common.csv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

/**
 * Minimal RFC 4180 CSV writer: comma-separated, CRLF line endings, fields quoted when they contain a comma,
 * quote or line break.
 * <p>
 * Text cells are also guarded against CSV/formula injection (OWASP): a value starting with {@code = + - @},
 * tab or carriage return is prefixed with {@code '} so spreadsheet apps show it as text instead of running it.
 * Number cells are written as-is, because a negative number is legitimate data.
 */
public final class CsvWriter {

    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    private final Writer out;
    private boolean rowStarted;

    public CsvWriter(Writer out) {
        this.out = out;
    }

    public CsvWriter text(String value) {
        if (value != null && !value.isEmpty() && FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return cell(value);
    }

    public CsvWriter number(String value) {
        return cell(value);
    }

    public void endRow() {
        write("\r\n");
        rowStarted = false;
    }

    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CsvWriter cell(String value) {
        if (rowStarted) {
            write(",");
        }
        rowStarted = true;
        if (value != null) {
            write(needsQuoting(value) ? "\"" + value.replace("\"", "\"\"") + "\"" : value);
        }
        return this;
    }

    private static boolean needsQuoting(String value) {
        return value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    private void write(String s) {
        try {
            out.write(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
