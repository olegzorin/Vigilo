package dev.olegz.vf.report.domain;

import java.util.Locale;

/** Shared report parameter and output-field data types. */
public enum ReportDataType {
    UNKNOWN(0),
    STRING(1),
    INTEGER(2),
    DATETIME(3),
    DECIMAL(4),
    BOOLEAN(5),
    DATE(6);

    private final byte databaseValue;

    ReportDataType(int databaseValue) {
        this.databaseValue = (byte) databaseValue;
    }

    public byte databaseValue() {
        return databaseValue;
    }

    public static ReportDataType fromDatabaseValue(int value) {
        for (ReportDataType type : values()) {
            if (type.databaseValue == value) return type;
        }
        return UNKNOWN;
    }

    public static ReportDataType fromName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("report data type is required");
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown report data type: " + name, e);
        }
    }
}
