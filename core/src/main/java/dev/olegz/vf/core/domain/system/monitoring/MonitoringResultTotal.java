package dev.olegz.vf.core.domain.system.monitoring;

import java.sql.Timestamp;

public class MonitoringResultTotal {
    public Timestamp monitoringDate;
    public String cloud;
    public String server;
    public String type;
    public int total;
    public int noResponse;
    public int badCondition;
    public int unexpectedError;

    public MonitoringResultTotal(long startOfDay, MonitoringResult res) {
        this.monitoringDate = new Timestamp(startOfDay);
        this.cloud = res.cloud;
        this.server = res.server;
        this.type = res.type;
        merge(res);
    }

    public MonitoringResultTotal merge(MonitoringResult res) {
        this.total++;

        switch (res.status) {
            case MonitoringResult.STATUS_NO_RESPONSE -> this.noResponse++;
            case MonitoringResult.STATUS_BAD_CONDITION -> this.badCondition++;
            case MonitoringResult.STATUS_UNEXPECTED_ERROR -> this.unexpectedError++;
        }

        return this;
    }

    @Override
    public String toString() {
        return "{monitoringDate=" + monitoringDate +
            ", cloud=" + cloud +
            ", server=" + server +
            ", type=" + type +
            ", total=" + total +
            ", noResponse=" + noResponse +
            ", badCondition=" + badCondition +
            ", unexpectedError=" + unexpectedError +
            '}';
    }
}
