package com.acme.salary.common.csv;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    private final StringWriter out = new StringWriter();
    private final CsvWriter csv = new CsvWriter(out);

    @Test
    void separatesCellsWithCommasAndEndsRowsWithCrlf() {
        csv.text("E00001").text("Ada").number("95000.00").endRow();
        csv.text("E00002").text("Alan").number("1200").endRow();
        csv.flush();

        assertThat(out).hasToString("E00001,Ada,95000.00\r\nE00002,Alan,1200\r\n");
    }

    @Test
    void quotesCellsContainingCommasQuotesOrLineBreaks() {
        csv.text("Head of FP&A, EMEA").text("Say \"hi\"").text("line1\nline2").endRow();
        csv.flush();

        assertThat(out).hasToString("\"Head of FP&A, EMEA\",\"Say \"\"hi\"\"\",\"line1\nline2\"\r\n");
    }

    @Test
    void neutralisesSpreadsheetFormulasInTextCells() {
        csv.text("=HYPERLINK(\"http://evil\")").text("+44 20").text("-1+2").text("@SUM(A1)")
                .text("\tTab").text("\rCR").endRow();
        csv.flush();

        assertThat(out.toString().split(",", -1)).containsExactly(
                "\"'=HYPERLINK(\"\"http://evil\"\")\"", "'+44 20", "'-1+2", "'@SUM(A1)", "'\tTab", "\"'\rCR\"\r\n");
    }

    @Test
    void leavesNumbersUntouchedEvenWhenNegative() {
        csv.number("-12.50").endRow();
        csv.flush();

        assertThat(out).hasToString("-12.50\r\n");
    }

    @Test
    void writesNullsAsEmptyCells() {
        csv.text(null).number(null).text("x").endRow();
        csv.flush();

        assertThat(out).hasToString(",,x\r\n");
    }

    @Test
    void keepsUnicodeIntact() {
        csv.text("Müller").text("佐藤").endRow();
        csv.flush();

        assertThat(out).hasToString("Müller,佐藤\r\n");
    }
}
