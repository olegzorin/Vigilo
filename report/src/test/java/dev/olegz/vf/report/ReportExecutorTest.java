package dev.olegz.vf.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportData;
import dev.olegz.vf.report.domain.ReportField;
import dev.olegz.vf.report.domain.ReportParam;

class ReportExecutorTest {
    @Test
    void evaluatesExpressionsAndPopulatesFields() {
        Report report = new Report();
        report.reportId = 17;
        report.sqlQuery = "select $J{enabled ? 'n1' : 'n2'$J};;;select n1";

        ReportParam enabled = new ReportParam();
        enabled.name = "enabled";
        enabled.index = 0;
        report.params = List.of(enabled);

        ReportField value = new ReportField();
        value.index = 0;
        value.columnName = "n1";
        value.dataType = ReportField.DATATYPE_DECIMAL;
        report.fields = List.of(value);

        ArrayList<String> queries = new ArrayList<>();
        ReportExecutor executor = new ReportExecutor((query, _) -> {
            queries.add(query);
            ReportData row = new ReportData();
            row.n1 = (double) queries.size();
            return List.of(row);
        });

        assertEquals(2, executor.execute(report, new Object[]{true}));
        assertEquals(List.of("select n1", "select n1"), queries);
        assertEquals(2.0, value.values[0]);
    }
}
