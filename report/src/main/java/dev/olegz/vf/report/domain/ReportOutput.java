package dev.olegz.vf.report.domain;

import java.nio.charset.StandardCharsets;
import java.util.List;

import tools.jackson.databind.ObjectWriter;
import tools.jackson.dataformat.csv.CsvMapper;
import dev.olegz.vf.common.ApplicationFailureException;

public class ReportOutput {
    private static final ObjectWriter CSV_WRITER = new CsvMapper().writerFor(String[].class);

    public final String[] headers;
    public final String[][] data;

    public ReportOutput(Report report) {
        List<ReportField> fields = report.getFieldsOrdered();
        int colCount = fields.size();

        headers = new String[colCount];
        for (int i = 0; i < colCount; i++) {
            headers[i] = fields.get(i).displayName;
        }

        int rowCount = fields.getFirst().getValuesCount();

        data = new String[rowCount][colCount];

        for (int j = 0; j < rowCount; j++) {
            for (int i = 0; i < colCount; i++) {
                data[j][i] = fields.get(i).getValueAsString(j);
            }
        }
    }

    public byte[] exportCsvWithHeaders() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append(makeCsvRow(headers));
        writeCsv(sb, data);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public String exportAnalyticCsv() {
        StringBuilder sb = new StringBuilder(100);
        writeCsv(sb, data);
        return sb.toString();
    }

    public static byte[] exportCsv(String[][] data) {
        StringBuilder sb = new StringBuilder(2048);
        writeCsv(sb, data);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void writeCsv(StringBuilder sb, String[][] data) {
        if (data != null) {
            for (String[] row : data) {
                sb.append(makeCsvRow(row));
            }
        }
    }

    private static String makeCsvRow(String[] row) {
        try {
            return CSV_WRITER.writeValueAsString(row);
        } catch (Exception e) {
            throw new ApplicationFailureException(e);
        }

    }
}