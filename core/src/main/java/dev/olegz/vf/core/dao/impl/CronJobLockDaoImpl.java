package dev.olegz.vf.core.dao.impl;

import java.sql.Timestamp;

import dev.olegz.vf.core.dao.CronJobLockDao;
import dev.olegz.vf.core.dao.mapper.CronJobLockMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CronJobLockDaoImpl implements CronJobLockDao {
    private final CronJobLockMapper mapper;

    public CronJobLockDaoImpl(CronJobLockMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean claimExpired(String jobName, String ownerId, Timestamp now, Timestamp lockUntil) {
        long currentTime = System.currentTimeMillis();
        return mapper.claimExpired(jobName, ownerId, now, lockUntil);
    }

    @Override
    public void insert(String jobName, String ownerId, Timestamp lockUntil) {
        mapper.insert(jobName, ownerId, lockUntil);
    }

    @Override
    public boolean renew(String jobName, String ownerId, Timestamp lockUntil) {
        return mapper.renew(jobName, ownerId, lockUntil);
    }

    @Override
    public boolean release(String jobName, String ownerId) {
        return mapper.release(jobName, ownerId);
    }
}
