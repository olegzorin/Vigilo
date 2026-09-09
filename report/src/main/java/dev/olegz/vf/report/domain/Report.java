package dev.olegz.vf.report.domain;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.exception.MissingParameterException;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.registry.domain.account.Organization;

public class Report {
    public static final byte TYPE_SUMMARY = 0;
    public static final byte TYPE_ANALYTICS = 1;

    public enum Type {
        SUMMARY(TYPE_SUMMARY),
        ANALYTICS(TYPE_ANALYTICS);

        private final byte databaseValue;

        Type(byte databaseValue) {
            this.databaseValue = databaseValue;
        }

        public byte databaseValue() {
            return databaseValue;
        }

        public static Type fromName(String name) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("report type is required");
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown report type: " + name, e);
            }
        }
    }

    public static class Dictionary {
        public final int id;
        public final String name;
        public final String description;

        public Dictionary(int id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }
    }

    public int reportId;
    public String reportName;
    public String displayName;
    public String description;
    public byte reportType;
    public String sqlQuery;
    public String displayInfo;
    public String dictionarySql;
    public List<ReportParam> params;
    public List<ReportField> fields;
    public List<ReportGroupSchedule> reportGroupSchedules;
    public List<ReportExecution> executions;
    public List<ReportMetadata> metadata;

    public boolean isAnalytic() {
        return reportType == TYPE_ANALYTICS;
    }

    public List<ReportParam> getParamsOrdered() {
        return (params == null) || (params.size() < 2) ? params :
            params.stream().sorted(Comparator.comparingInt(rp -> rp.index)).toList();
    }

    public List<ReportField> getFieldsOrdered() {
        return fields.size() < 2 ? fields :
            fields.stream().sorted(Comparator.comparingInt(rp -> rp.index)).toList();
    }

    @Override
    public String toString() {
        return "{reportId=" + reportId +
            ", reportName=" + reportName +
            ", reportType=" + reportType +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        return (this == o) ||
            (o instanceof Report report) &&
                this.reportId == report.reportId &&
                this.reportType == report.reportType &&
                Objects.equals(this.reportName, report.reportName) &&
                Objects.equals(this.displayName, report.displayName) &&
                Objects.equals(this.description, report.description);
    }

    @Override
    public int hashCode() {
        throw new ApplicationFailureException("hashCode not designed for " + this.getClass().getName());
    }

    public Object[] getParamValues(Map<String, String> strParams, long lastTime, Integer organizationId, ZoneId zoneId) {
        Object[] paramValues = null;
        if ((params != null) && !params.isEmpty()) {
            paramValues = new Object[params.size()];
            for (ReportParam param : params) {
                if ((param.index < 0) || (param.index >= params.size())) {
                    throw new ApplicationFailureException("Parameter index out of range: reportId=" + reportId + ", name=" + param.name + ", index=" + param.index);
                }

                Object value;

                if (ReportParam.PARAM_ORGANIZATION_ID.equals(param.name) && (organizationId != null)) {
                    value = organizationId;
                } else {
                    String strValue = strParams == null ? null : StringUtils.trimToNull(strParams.get(param.name));
                    try {
                        value = param.getValue(strValue, lastTime, organizationId, zoneId);
                    } catch (Exception e) {
                        throw new WrongParameterValueException("Cannot parse parameter value, name=" + param.name + ", value=" + strValue + " : " + e);
                    }
                }

                if ((value == null) && param.required) {
                    throw new MissingParameterException("Missing required parameter value, name=" + param.name);
                }

                paramValues[param.index] = value;
            }
        }
        return paramValues;
    }

    public int getOrgParamIndex() {
        if (params != null) {
            for (var param : params) {
                if (ReportParam.PARAM_ORGANIZATION_ID.equals(param.name)) {
                    return param.index;
                }
            }
        }
        return -1;
    }

    public HashMap<String, Object> getModel(Object[] values, Function<Integer, Organization> organizationById, String message) {
        List<String[]> paramsModel = CollectionOps.map(getParamsOrdered(),
            p -> p.getModel(values[p.index], organizationById)
        );

        HashMap<String, Object> model = new HashMap<>();
        model.put("title", StringEscapeUtils.escapeHtml4(displayName));
        model.put("description", StringEscapeUtils.escapeHtml4(description));
        model.put("parameters", paramsModel != null ? paramsModel : List.of());
        model.put("message", StringEscapeUtils.escapeHtml4(StringUtils.defaultString(message)));
        return model;
    }

    public Map<String, String> computeMetadata() {
        if ((metadata == null) || metadata.isEmpty()) return null;

        HashMap<String, String> res = new HashMap<>(metadata.size());

        for (ReportMetadata m : metadata) {
            ReportField field = CollectionOps.findAny(fields, f -> f.index == m.field_index);
            if (field == null) continue;

            res.put(m.name, Long.toString(m.compute(field.values)));
        }
        return res.isEmpty() ? null : res;
    }
}
