package dev.olegz.vf.report.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.util.function.Function;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.report.ReportDateTime;

public class ReportParam {
    public static final String PARAM_ORGANIZATION_ID = "organizationId";
    private static final String PARAM_ORG_ID = "orgId";
    private static final String PARAM_PERIOD = "period";

    public static final byte DATATYPE_INTEGER = ReportDataType.INTEGER.databaseValue();
    public static final byte DATATYPE_DATETIME = ReportDataType.DATETIME.databaseValue();

    public int reportId;
    public String name;
    public int index;
    public int dataType;
    public boolean required;
    public String displayName;
    public String description;
    public String placeholder;

    Object getValue(String strValue, long lastTime, Integer organizationId, ZoneId zoneId) {
        return PARAM_ORGANIZATION_ID.equals(name) ? organizationId :
            ((strValue = StringUtils.trimToNull(strValue)) == null) ? null :
                switch (ReportDataType.fromDatabaseValue(dataType)) {
                    case BOOLEAN -> Boolean.parseBoolean(strValue);
                    case INTEGER -> new BigInteger(strValue).intValue();
                    case DECIMAL -> new BigDecimal(strValue).doubleValue();
                    case DATETIME -> ReportDateTime.parseReportTimestamp(strValue, lastTime, zoneId);
                    default -> strValue;
                };
    }

    String[] getModel(Object value, Function<Integer, Organization> organizationById) {
        return new String[] {
            "#" + (1 + index),
            StringEscapeUtils.escapeHtml4(displayName),
            StringEscapeUtils.escapeHtml4(valueToString(value)),
            StringEscapeUtils.escapeHtml4(getMeaning(value, organizationById))
        };
    }

    private String valueToString(Object value) {
        return switch (ReportDataType.fromDatabaseValue(dataType)) {
            case BOOLEAN -> value == null ? "false" : value.toString();
            case INTEGER -> value instanceof Integer i ? Integer.toString(i) : "";
            case DECIMAL -> value != null ? new DecimalFormat("#").format(value) : "";
            case DATETIME ->
                value instanceof Timestamp ts ? DateFormatUtils.printDateTime(ts.getTime()) :
                    (value instanceof Long tm) && (tm > 0) ? DateFormatUtils.printDateTime(tm) : "";
            default -> value != null ? value.toString() : "";
        };
    }

    private String getMeaning(Object value, Function<Integer, Organization> organizationById) {
        String meaning = null;
        if (PARAM_ORGANIZATION_ID.equals(name) || PARAM_ORG_ID.equals(name)) {
            if (value instanceof Integer orgId) {
                Organization organization = organizationById.apply(orgId);
                meaning = organization != null ? StringUtils.defaultIfEmpty(organization.organizationName, name) : name;
            }
        } else if (PARAM_PERIOD.equals(name)) {
            if (value instanceof Integer period) {
                meaning = switch (period) {
                    case 1 -> "Daily";
                    case 2 -> "Weekly";
                    case 3 -> "Monthly";
                    case 4 -> "Annual";
                    default -> "";
                };
            }
        }
        return StringUtils.defaultIfEmpty(meaning, StringUtils.defaultString(description));
    }
}
