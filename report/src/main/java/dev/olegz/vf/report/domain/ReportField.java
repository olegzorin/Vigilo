package dev.olegz.vf.report.domain;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;

import dev.olegz.vf.report.ReportDateTime;

public class ReportField {
    public static final String ID_FIELD_NAME = "#id";
    public static final String VALUE_FIELD_NAME = "#value";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final byte DATATYPE_INTEGER = ReportDataType.INTEGER.databaseValue();
    public static final byte DATATYPE_DECIMAL = ReportDataType.DECIMAL.databaseValue();

    public int reportId;
    public String name;
    public int index;
    public int dataType;
    public String columnName;
    public String displayName;
    public String description;
    public Object[] values;

    @Override
    public String toString() {
        return "{reportId=" + reportId +
            ", name=" + name +
            ", index=" + index +
            ", dataType=" + dataType +
            ", columnName=" + columnName +
            ", displayName=" + displayName +
            ", description=" + description +
            ", values=" + Arrays.toString(values) +
            '}';
    }

    public void putValue(Object value, int rowIndex, int maxRows) {
        if (values == null) values = new Object[maxRows];
        else if (values.length < maxRows) values = Arrays.copyOf(values, maxRows);
        if (value != null) values[rowIndex] = value;
    }

    public int getValuesCount() {
        return values == null ? 0 : values.length;
    }

    public String getValueAsString(int ind) {
        if ((values == null) || (ind >= values.length) || (values[ind] == null)) return "";

        Object value = values[ind];

        return switch (ReportDataType.fromDatabaseValue(dataType)) {
            case BOOLEAN ->
                    value instanceof Boolean b ? b.toString() :
                    value instanceof Number n ? String.valueOf(n.intValue() != 0) :
                    value.toString().toLowerCase();
            case INTEGER -> new DecimalFormat("#").format(value);
            case DECIMAL -> new DecimalFormat("#.0####").format(value);
            case DATETIME -> ((Date)value).toInstant().atZone(ReportDateTime.DEFAULT_ZONE_ID).format(DATE_TIME_FORMATTER);
            case DATE -> ((Date)value).toInstant().atZone(ReportDateTime.DEFAULT_ZONE_ID).format(DATE_FORMATTER);
            default -> value.toString();
        };
    }

    public static Object readValueFromString(int dataType, String str) {
        if ((str == null) || str.isBlank()) return null;

        str = str.trim();

        return switch (ReportDataType.fromDatabaseValue(dataType)) {
            case BOOLEAN -> Boolean.parseBoolean(str);
            case INTEGER -> (int) Double.parseDouble(str);
            case DECIMAL -> Double.parseDouble(str);
            default -> str;
        };
    }
}
