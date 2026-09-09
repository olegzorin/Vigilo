package dev.olegz.vf.report.domain;

import java.sql.Timestamp;
import java.util.Map;

public class ReportExecution {
    public static final int MAX_ERROR_MSG_LEN = 250;

    public int reportId;
    public String reportName;
    public boolean multiCloud;
    public Integer scheduleId;
    public Timestamp executionDate;
    public Timestamp scheduleDate;
    public Integer organizationId;
    public int executionTime;
    public byte[] blobOutput;
    public String analyticOutput;
    public String objectId;
    public boolean onDemand;
    public String errorMessage;
    public int rowCount;
    public Map<String, String> metadata;

    public ReportExecution() {
    }

    public ReportExecution(ReportRequest reportRequest) {
        this.reportId = reportRequest.reportId;
        this.organizationId = reportRequest.organizationId;
        if (reportRequest.scheduleDate != null) this.scheduleDate = new Timestamp(reportRequest.scheduleDate);
        this.objectId = reportRequest.objectId;
        switch (reportRequest.requestType) {
            case ReportRequest.TYPE_SCHEDULE -> {
                this.scheduleId = reportRequest.scheduleId;
                this.multiCloud = reportRequest.multiCloud;
            }
            case ReportRequest.TYPE_DEMAND -> this.onDemand = true;
        }
    }

    @Override
    public String toString() {
        return "{reportId=" + reportId +
            ", reportName=" + reportName +
            ", rowCount=" + rowCount +
            ", scheduleDate=" + scheduleDate +
            ", executionDate=" + executionDate +
            (onDemand ? ", onDemand" : "") +
            (multiCloud ? ", multiCloud" : "") +
            (blobOutput != null ? ", blobOutput" : "") +
            (analyticOutput != null ? ", analyticOutput" : "") +
            (scheduleId != null ? ", scheduleId=" + scheduleId : "") +
            (organizationId != null ? ", organizationId=" + organizationId : "") +
            '}';
    }

}
