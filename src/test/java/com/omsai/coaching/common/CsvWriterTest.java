package com.omsai.coaching.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CSV writer used by the exports: quoting of separators, quotes and
 * line breaks, neutralising spreadsheet formulas and honest handling of
 * empty values.
 */
class CsvWriterTest {

    @Test
    void plainFieldsAreWrittenUnquoted() {
        String csv = CsvWriter.create()
                .header("Code", "Name")
                .row("OMS-2026-00001", "Aarav Sharma")
                .toCsv();

        assertThat(csv).isEqualTo("Code,Name\nOMS-2026-00001,Aarav Sharma\n");
    }

    @Test
    void fieldsWithSeparatorsAreQuoted() {
        String csv = CsvWriter.create()
                .header("Name")
                .row("Sharma, Aarav")
                .toCsv();

        assertThat(csv).contains("\"Sharma, Aarav\"");
    }

    @Test
    void embeddedQuotesAreDoubled() {
        String csv = CsvWriter.create()
                .header("Name")
                .row("He said \"hello\"")
                .toCsv();

        assertThat(csv).contains("\"He said \"\"hello\"\"\"");
    }

    @Test
    void newlinesInsideFieldsAreKeptInsideQuotedFields() {
        String csv = CsvWriter.create()
                .header("Remarks")
                .row("first line\nsecond line")
                .toCsv();

        assertThat(csv).contains("\"first line\nsecond line\"");
        // The record itself stays on one physical line for the record
        // separator, the embedded break is inside the quoted field.
        assertThat(csv.split("\n")).hasSize(3);
    }

    @Test
    void formulaLikeValuesAreNeutralisedButStayReadable() {
        String csv = CsvWriter.create()
                .header("Name")
                .row("=1+1")
                .row("+SUM(A1)")
                .row("-2+3")
                .row("@cmd")
                .toCsv();

        assertThat(csv).contains("'=1+1");
        assertThat(csv).contains("'+SUM(A1)");
        assertThat(csv).contains("'-2+3");
        assertThat(csv).contains("'@cmd");
    }

    @Test
    void ordinaryValuesAreNeverAltered() {
        String csv = CsvWriter.create()
                .header("Name", "Percentage", "Date")
                .row("Aarav Sharma", "91.7", "2026-09-10")
                .toCsv();

        assertThat(csv).contains("Aarav Sharma,91.7,2026-09-10");
        assertThat(csv).doesNotContain("'");
    }

    @Test
    void nullValuesBecomeEmptyFields() {
        String csv = CsvWriter.create()
                .header("Name", "Percentage")
                .row(null, "")
                .toCsv();

        assertThat(csv.split("\n")[1]).isEqualTo(",");
    }

    @Test
    void anEmptyWriterProducesNoDocument() {
        assertThat(CsvWriter.create().toCsv()).isEmpty();
    }
}
