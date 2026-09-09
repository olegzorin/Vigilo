package dev.olegz.vf.report.domain;

import java.util.Objects;
import java.util.stream.Stream;

public class ReportMetadata {
    private static final byte FUNC_COUNT = 0;
    private static final byte FUNC_COUNT_DISTINCT = 1;
    private static final byte FUNC_SUM = 2;

    public int reportId;
    public String name;
    public byte func_index;
    public int field_index;
    public String description;

    public long compute(Object[] values) {
        if ((values == null) || (values.length == 0)) return 0;

        if (func_index == FUNC_COUNT) {
            return Stream.of(values).filter(Objects::nonNull).count();
        } else if (func_index == FUNC_COUNT_DISTINCT) {
            return Stream.of(values).filter(Objects::nonNull).distinct().count();
        } else if (func_index == FUNC_SUM) {
            return Stream.of(values).filter(v -> v instanceof Double).mapToLong(v -> ((Double) v).longValue()).sum();
        }

        return 0;
    }
}