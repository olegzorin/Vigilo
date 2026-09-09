package dev.olegz.vf.worker.scheduler;

import java.sql.Timestamp;
import java.text.ParseException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.common.schedule.CronExpression;
import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.core.dao.CronJobLockDao;
import dev.olegz.vf.worker.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Cron job scheduler backed by a {@link ScheduledThreadPoolExecutor}.
 * <p>
 * Schedules are kept in memory (producers re-publish active schedules on startup). Coordination
 * for jobs that disallow concurrent execution is backed by the database. Cron parsing and
 * next-fire computation use {@link CronExpression}.
 * <p>
 * Each {@link CronTask} re-schedules its own next fire after running. A {@code CronTask} owns its
 * scheduling state and the lock that guards it; a per-task generation counter guards against
 * double-scheduling when a job is rescheduled while a fire is in flight.
 */
@Component
public class ExecutorCronScheduler {
    private static final Logger logger = LoggerFactory.getLogger(ExecutorCronScheduler.class);

    private final int threadCount;
    private final List<CronJob> cronJobs;
    private final CronJobLockDao jobLockDao;
    private final Duration jobLockLease;
    private final Map<String, CronTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, JobDefinition> jobs = new ConcurrentHashMap<>();
    private volatile ScheduledThreadPoolExecutor executor;
    private volatile ScheduledThreadPoolExecutor lockRenewalExecutor;
    private volatile boolean started;

    public ExecutorCronScheduler(List<CronJob> cronJobs, CronJobLockDao jobLockDao) {
        this(cronJobs, jobLockDao, PropertyStore.getDuration(DurationProp.SCHEDULER_JOB_LOCK_LEASE));
    }

    ExecutorCronScheduler(List<CronJob> cronJobs, CronJobLockDao jobLockDao, Duration jobLockLease) {
        this.threadCount = Math.max(1, PropertyStore.getInt("vf.scheduler.threadCount", 10));
        this.cronJobs = cronJobs;
        this.jobLockDao = jobLockDao;
        this.jobLockLease = jobLockLease;
    }

    public void start() {
        logger.debug(">start()");

        if (started) {
            logger.warn("Executor cron scheduler is started already");
            return;
        }
        started = true;
        ScheduledThreadPoolExecutor ex = new ScheduledThreadPoolExecutor(threadCount);
        ex.setRemoveOnCancelPolicy(true);
        executor = ex;
        ScheduledThreadPoolExecutor renewalEx = new ScheduledThreadPoolExecutor(1);
        renewalEx.setRemoveOnCancelPolicy(true);
        lockRenewalExecutor = renewalEx;

        loadStaticJobs();

        logger.warn("Executor cron scheduler started: threadCount=" + threadCount);
        logger.debug("<start()");
    }

    public void shutdown() {
        logger.debug(">shutdown()");

        started = false;
        tasks.clear();
        jobs.clear();
        ScheduledThreadPoolExecutor ex = executor;
        if (ex != null) {
            ex.shutdownNow();
            executor = null;
        }
        ScheduledThreadPoolExecutor renewalEx = lockRenewalExecutor;
        if (renewalEx != null) {
            renewalEx.shutdownNow();
            lockRenewalExecutor = null;
        }

        logger.debug("<shutdown()");
    }

    public void scheduleCronJob(String name, String cronExpression, String timeZoneId) {
        logger.debug(">scheduleCronJob() name={}, cron={}, timeZoneId={}", name, cronExpression, timeZoneId);

        if (name == null || name.isBlank() || (cronExpression == null)) {
            logger.error("Empty cron job: name=" + name + ", cron=" + cronExpression);
            return;
        }
        if (!started || (executor == null)) {
            logger.error("Scheduler is not started, cannot schedule " + name);
            return;
        }
        if (!jobs.containsKey(name)) {
            logger.error("No scheduled job registered for name " + name);
            return;
        }

        CronExpression cron;
        try {
            cron = new CronExpression(cronExpression);
        } catch (ParseException e) {
            logger.error("Invalid cron expression for job " + name + ": " + cronExpression + " : " + e.getMessage());
            return;
        }

        TimeZone timeZone = timeZoneId != null ? TimeZone.getTimeZone(timeZoneId) : DateFormatUtils.DEFAULT_TIMEZONE;

        CronTask ct = tasks.computeIfAbsent(name, CronTask::new);
        ct.schedule(cronExpression, cron, timeZone);

        logger.debug("<scheduleCronJob()");
    }

    /**
     * Register and schedule the static cron jobs discovered by Spring.
     */
    protected void loadStaticJobs() {
        for (CronJob job : cronJobs) {
            registerJob(job.name(), job, job.disallowConcurrent());
            scheduleCronJob(job.name(), job.cron(), job.timeZoneId());
        }
    }

    protected void registerJob(String jobName, Runnable job, boolean disallowConcurrent) {
        jobs.put(jobName, new JobDefinition(job, disallowConcurrent));
    }

    boolean hasJob(String jobName) {
        return jobs.containsKey(jobName);
    }

    /**
     * Per-job scheduling state. Owns the lock that guards its own (re)scheduling; the job execution
     * itself runs outside the lock so a long-running job never blocks scheduling operations.
     */
    private final class CronTask {
        private final String jobName;
        private final AtomicBoolean running = new AtomicBoolean(false);

        // accessed outside the lock by runOnce()/scheduleNext() -> volatile
        private volatile String cronExpr;
        private volatile CronExpression cron;
        private volatile TimeZone timeZone;
        // accessed only while holding this monitor
        private ScheduledFuture<?> future;
        private int generation;

        private CronTask(String jobName) {
            this.jobName = jobName;
        }

        /** (Re)schedule with the given cron; no-op if the schedule is unchanged. */
        synchronized void schedule(String cronExpr, CronExpression cron, TimeZone timeZone) {
            if (cronExpr.equals(this.cronExpr) && timeZone.equals(this.timeZone) && (future != null)) {
                return;
            }
            generation++; // invalidate in-flight fires
            cancelFuture();
            this.cronExpr = cronExpr;
            this.cron = cron;
            this.timeZone = timeZone;
            scheduleNext(System.currentTimeMillis());
        }

        /** Compute the next fire time and schedule it. Caller must hold this monitor. */
        private void scheduleNext(long afterTime) {
            ScheduledThreadPoolExecutor ex = executor;
            if ((ex == null) || !started) return;

            long nextFire = cron.getTimeAfter(afterTime, timeZone);
            if (nextFire <= 0) {
                logger.warn("No future fire time for job " + jobName + ", cron=" + cronExpr);
                return;
            }

            long delay = Math.max(0L, nextFire - System.currentTimeMillis());
            final long scheduledFireTime = nextFire;
            final int gen = generation;
            future = ex.schedule(() -> fire(scheduledFireTime, gen), delay, TimeUnit.MILLISECONDS);

            if (logger.isDebugEnabled()) {
                logger.debug("Job " + jobName + " scheduled at " + DateFormatUtils.logTimestamp(new Date(nextFire)));
            }
        }

        /**
         * Scheduled-fire body: run once, then reschedule the next occurrence. Not synchronized - the
         * job must not execute while holding the lock; only the short stale-check and reschedule are.
         */
        private void fire(long scheduledFireTime, int gen) {
            try {
                if (!Application.isRunning()) return;
                if (!isCurrent(gen)) return; // rescheduled since it was scheduled
                runJobOnce(jobName, running);
            } finally {
                rescheduleAfterFire(scheduledFireTime, gen);
            }
        }

        private synchronized boolean isCurrent(int gen) {
            return (gen == generation) && (tasks.get(jobName) == this);
        }

        private synchronized void rescheduleAfterFire(long scheduledFireTime, int gen) {
            if ((gen == generation) && started && (tasks.get(jobName) == this)) {
                // base the next fire on the later of the slot just fired and now, so we never
                // re-fire the same slot and never busy-loop catching up after a long run
                scheduleNext(Math.max(scheduledFireTime, System.currentTimeMillis()));
            }
        }

        /** Cancel the pending future. Caller must hold this monitor. */
        private void cancelFuture() {
            if (future != null) {
                future.cancel(false);
                future = null;
            }
        }
    }

    void runJobOnce(String jobName, AtomicBoolean running) {
        JobDefinition job = jobs.get(jobName);
        if (job == null) {
            logger.error("No scheduled job registered: jobName=" + jobName);
            return;
        }

        if (!job.disallowConcurrent()) {
            runJob(jobName, job.job());
            return;
        }
        if (!running.compareAndSet(false, true)) {
            logger.debug("the job {} is running already in this worker", jobName);
            return;
        }

        String ownerId = null;
        ScheduledFuture<?> renewal = null;
        try {
            ownerId = tryAcquireLock(jobName);
            if (ownerId == null) {
                logger.debug("the job {} is running already in another worker", jobName);
                return;
            }
            renewal = scheduleLockRenewal(jobName, ownerId);
            runJob(jobName, job.job());
        } catch (Throwable e) {
            logger.error("Exception while acquiring or maintaining the lock for job " + jobName, e);
        } finally {
            if (renewal != null) renewal.cancel(false);
            if (ownerId != null) releaseLock(jobName, ownerId);
            running.set(false);
        }
    }

    private ScheduledFuture<?> scheduleLockRenewal(String jobName, String ownerId) {
        ScheduledThreadPoolExecutor renewalEx = lockRenewalExecutor;
        if (renewalEx == null) throw new IllegalStateException("Scheduler lock renewal executor is not running");

        long periodMillis = Math.max(100L, jobLockLease.toMillis() / 3);
        return renewalEx.scheduleAtFixedRate(() -> {
            try {
                Timestamp lockUntil = new Timestamp(System.currentTimeMillis() + jobLockLease.toMillis());
                if (!jobLockDao.renew(jobName, ownerId, lockUntil)) {
                    logger.error("Lost database lock for running job " + jobName + ", ownerId=" + ownerId);
                }
            } catch (Throwable e) {
                logger.error("Could not renew database lock for running job " + jobName, e);
            }
        }, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    private String tryAcquireLock(String jobName) {
        String ownerId = UUID.randomUUID().toString();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp lockUntil = new Timestamp(now.getTime() + jobLockLease.toMillis());
        if (jobLockDao.claimExpired(jobName, ownerId, now, lockUntil)) return ownerId;

        try {
            jobLockDao.insert(jobName, ownerId, lockUntil);
            return ownerId;
        } catch (DuplicateKeyException e) {
            return null;
        }
    }

    private void releaseLock(String jobName, String ownerId) {
        try {
            if (!jobLockDao.release(jobName, ownerId)) {
                logger.warn("Database lock was not owned when releasing job " + jobName + ", ownerId=" + ownerId);
            }
        } catch (Throwable e) {
            logger.error("Could not release database lock for job " + jobName, e);
        }
    }

    private void runJob(String jobName, Runnable job) {
        try {
            job.run();
        } catch (Throwable e) {
            logger.error("Exception in running job " + jobName, e);
        }
    }

    private record JobDefinition(Runnable job, boolean disallowConcurrent) {}
}
