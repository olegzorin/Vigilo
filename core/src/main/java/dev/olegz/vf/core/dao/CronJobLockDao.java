package dev.olegz.vf.core.dao;

import java.sql.Timestamp;

public interface CronJobLockDao {
    boolean claimExpired(String jobName, String ownerId, Timestamp now, Timestamp lockUntil);
    void insert(String jobName, String ownerId, Timestamp lockUntil);
    boolean renew(String jobName, String ownerId, Timestamp lockUntil);
    boolean release(String jobName, String ownerId);
}
