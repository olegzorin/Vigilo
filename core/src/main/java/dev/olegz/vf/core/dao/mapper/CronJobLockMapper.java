package dev.olegz.vf.core.dao.mapper;

import java.sql.Timestamp;

import org.apache.ibatis.annotations.Param;

public interface CronJobLockMapper {
    boolean claimExpired(
        @Param("jobName") String jobName,
        @Param("ownerId") String ownerId,
        @Param("now") Timestamp now,
        @Param("lockUntil") Timestamp lockUntil);
    void insert(
        @Param("jobName") String jobName,
        @Param("ownerId") String ownerId,
        @Param("lockUntil") Timestamp lockUntil);
    boolean renew(
        @Param("jobName") String jobName,
        @Param("ownerId") String ownerId,
        @Param("lockUntil") Timestamp lockUntil);
    boolean release(
        @Param("jobName") String jobName,
        @Param("ownerId") String ownerId);
}
