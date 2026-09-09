package dev.olegz.vf.core.domain.lambdarun;

import java.sql.Timestamp;

public class CloudWatchLog {
    public String groupName;
    public String streamName;
    public Timestamp endDate;
    public Integer retentionInDays;
    public Long lastEventTimestamp;
}
