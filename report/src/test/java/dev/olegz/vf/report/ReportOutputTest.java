package dev.olegz.vf.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportField;
import dev.olegz.vf.report.domain.ReportOutput;
import dev.olegz.vf.report.output.AnalyticReportOutput;

class ReportOutputTest {
    @Test
    void exportsHeadersAndEscapedCsvValues() {
        Report report = report(
            field("name", "Name", 0, 1, "Alice", "Bob, Jr."),
            field("count", "Count", 1, ReportField.DATATYPE_INTEGER, 2, 3));

        String csv = new String(new ReportOutput(report).exportCsvWithHeaders(), StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("Name,Count"));
        assertTrue(csv.contains("\"Bob, Jr.\",3"));
    }

    @Test
    void readsBothAnalyticOutputShapes() {
        Report multiRow = report(
            field(ReportField.ID_FIELD_NAME, "Id", 0, ReportField.DATATYPE_INTEGER),
            field(ReportField.VALUE_FIELD_NAME, "Value", 1, ReportField.DATATYPE_DECIMAL));
        AnalyticReportOutput output = AnalyticReportOutput.getReader(multiRow).readReportData("1,2.5\n2,3.5\n");
        assertEquals(2, output.items.size());
        assertEquals(new AnalyticReportOutput.Item(1, 2.5), output.items.getFirst());

        Report singleRow = report(
            field("first", "First", 0, ReportField.DATATYPE_INTEGER),
            field("second", "Second", 1, ReportField.DATATYPE_DECIMAL));
        output = AnalyticReportOutput.getReader(singleRow).readReportData("7,4.25");
        assertArrayEquals(new Object[]{7, 4.25}, output.items.stream().map(AnalyticReportOutput.Item::value).toArray());
        assertNull(AnalyticReportOutput.getReader(singleRow).readReportData(""));
    }

    private static Report report(ReportField... fields) {
        Report report = new Report();
        report.reportId = 99;
        report.fields = List.of(fields);
        return report;
    }

    private static ReportField field(String name, String displayName, int index, int type, Object... values) {
        ReportField field = new ReportField();
        field.name = name;
        field.displayName = displayName;
        field.index = index;
        field.dataType = type;
        field.values = values;
        return field;
    }
}
