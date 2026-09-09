package dev.olegz.vf.report.domain;

import java.util.Arrays;
import java.security.SecureRandom;

import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.report.ReportDateTime;

public class ReportRequest {
    public static final byte TYPE_SCHEDULE = 0;
    public static final byte TYPE_DEMAND = 1;

    public byte requestType;
    public int reportId;
    public boolean multiCloud;
    public int scheduleId;
    public Long scheduleDate;
    public Integer organizationId;
    public Long executionDate;
    public Object[] params;
    public String[] emails;
    public String objectId;

    @Override
    public String toString() {
        return "{requestType=" + requestType + ", reportId=" + reportId +
            (multiCloud ? ", multiCloud" : "") +
            (scheduleId > 0 ? ", scheduleId=" + scheduleId : "") +
            (scheduleDate != null ? ", scheduleDate=" + DateFormatUtils.logTimestamp(scheduleDate) : "") +
            (organizationId != null ? ", organizationId=" + organizationId : "") +
            (executionDate != null ? ", executionDate=" + DateFormatUtils.logTimestamp(executionDate) : "") +
            ", params=" + Arrays.toString(params) +
            (emails != null ? ", emails=" + Arrays.toString(emails) : "") +
            (objectId != null ? ", objectId=" + objectId : "") +
            '}';
    }

    public String makeObjectId() {
        return randomPrefix() + '-' + reportId + '-' + (organizationId != null ? organizationId : 0) +
            '-' + Long.toHexString(ReportDateTime.currentTimeMillis() / 1000L);
    }

    private static String randomPrefix() {
        final String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        SecureRandom random = new SecureRandom();
        char[] value = new char[4];
        for (int i = 0; i < value.length; i++) value[i] = alphabet.charAt(random.nextInt(alphabet.length()));
        return new String(value);
    }
}
