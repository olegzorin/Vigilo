package dev.olegz.vf.core.domain.system.monitoring;

public class MonitoringResult {
    public static final byte STATUS_OK = 0;
    public static final byte STATUS_NO_RESPONSE = 1;
    static final byte STATUS_BAD_CONDITION = 2;
    static final byte STATUS_UNEXPECTED_ERROR = 3;

    public byte status;
    public long time;
    public String cloud;
    public String server;
    public String type;

    public String key(long startOfDay) {
        return cloud + ':' + server + ':' + type + ':' + Long.toHexString(startOfDay / 1000L);
    }
}
