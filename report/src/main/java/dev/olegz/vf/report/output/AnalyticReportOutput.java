package dev.olegz.vf.report.output;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.ObjectReader;
import tools.jackson.dataformat.csv.CsvMapper;
import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportField;

public final class AnalyticReportOutput {
    private static final ObjectReader objectReader = new CsvMapper().readerFor(String[].class);

    public record Item (Object id, Object value) {}

    public final List<Item> items;

    private AnalyticReportOutput(int size) {
        items = new ArrayList<>(size);
    }

    private void addItem(Object id, Object value) {
        items.add(new Item(id, value));
    }

    public static Reader getReader(Report report) {
        List<ReportField> reportFields = report.getFieldsOrdered();
        boolean multiRowOutput = ReportField.ID_FIELD_NAME.equals(reportFields.get(0).name);

        if (multiRowOutput && !((reportFields.size() == 2) && ReportField.VALUE_FIELD_NAME.equals(reportFields.get(1).name))) {
            throw new ApplicationFailureException("Wrong multi-row report fields, names=" + reportFields.stream().map(rf -> rf.name).toList());
        }
        int[] fieldTypes = reportFields.stream().mapToInt(rf -> rf.dataType).toArray();
        return new Reader(report.reportId, fieldTypes, multiRowOutput);
    }

    public static class Reader {
        private final int reportId;
        private final int[] fieldTypes;
        private final boolean multiRowOutput;

        private Reader(int reportId, int[] fieldTypes, boolean multiRowOutput) {
            this.reportId = reportId;
            this.fieldTypes = fieldTypes;
            this.multiRowOutput = multiRowOutput;
        }

        public AnalyticReportOutput readReportData(String csvData) {
            if ((csvData == null) || csvData.isEmpty()) return null;

            AnalyticReportOutput output;
            try {
                if (multiRowOutput) {
                    // Output is a two-column table, where the first column
                    // contains identifiers and the second contains values.
                    List<String> dataRows = csvData.lines().toList();
                    output = new AnalyticReportOutput(dataRows.size());
                    for (String row : dataRows) {
                        if (row.isEmpty()) continue;
                        String[] rowValues = objectReader.readValue(row);
                        if ((rowValues == null) || (rowValues.length != 2)) {
                            throw new IOException("Wrong row in multi-output: " + row);
                        }
                        output.addItem(
                            ReportField.readValueFromString(fieldTypes[0], rowValues[0]),
                            ReportField.readValueFromString(fieldTypes[1], rowValues[1])
                        );
                    }
                } else {
                    // Output is a single row. The column index is treated as an identifier.
                    String[] csvRow = objectReader.readValue(csvData);
                    output = new AnalyticReportOutput(csvRow.length);
                    for (int i = 0; i < csvRow.length; i++) {
                        output.addItem(i + 1,
                            ReportField.readValueFromString(fieldTypes[i], csvRow[i])
                        );
                    }
                }
            } catch (Exception e) {
                throw new ApplicationFailureException("Exception while reading output of report " + reportId + " data=" + csvData, e);
            }
            return output;
        }
    }
}
