package dev.olegz.vf.worker.scheduler;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.core.dao.CronJobLockDao;
import dev.olegz.vf.core.service.lambda.LambdaClientService;
import dev.olegz.vf.worker.scheduler.job.CleanupObsoleteLambdaDeploymentsJob;
import dev.olegz.vf.worker.scheduler.job.DispatchExpiredLambdaRunCompletionsJob;
import dev.olegz.vf.worker.scheduler.job.FailExpiredLambdaCodeUploadsJob;
import dev.olegz.vf.worker.service.LambdaDeploymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standalone (no Spring, no database) test of {@link ExecutorCronScheduler}.
 */
class ExecutorCronSchedulerTest {

    private static final String EVERY_SECOND = "0/1 * * * * ?";
    private static final String NEVER_SOON = "0 0 0 1 1 ?"; // Jan 1 midnight - far in the future
    private static final String TZ = DateFormatUtils.DEFAULT_TIMEZONE_ID;

    private ExecutorCronScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) scheduler.shutdown();
    }

    private ExecutorCronScheduler startScheduler() {
        scheduler = new ExecutorCronScheduler(
            List.of(), new InMemoryCronJobLockDao(), Duration.ofMinutes(1));
        scheduler.start();
        return scheduler;
    }

    @Test
    void registersInjectedStaticJobs() {
        CronJob job = new CronJob() {
            @Override public String name() { return "test-static-job"; }
            @Override public String cron() { return NEVER_SOON; }
            @Override public void run() {}
        };
        scheduler = new ExecutorCronScheduler(
            List.of(job), new InMemoryCronJobLockDao(), Duration.ofMinutes(1));
        scheduler.start();

        assertTrue(scheduler.hasJob("test-static-job"),
            "injected static jobs should be registered on start");
    }

    @Test
    void purgeLambdaDeploymentsRunsProcessingDirectly() {
        AtomicBoolean purged = new AtomicBoolean(false);
        LambdaDeploymentService service = new LambdaDeploymentService(null, null, null, null, null) {
            @Override
            public void purgeDeployments() {
                purged.set(true);
            }
        };

        new CleanupObsoleteLambdaDeploymentsJob(service).run();

        assertTrue(purged.get(), "job should invoke purge processing directly");
    }

    @Test
    void failExpiredLambdaCodeUploadsRunsEveryThirtySeconds() {
        AtomicBoolean called = new AtomicBoolean(false);
        LambdaDeploymentService service = new LambdaDeploymentService(null, null, null, null, null) {
            @Override
            public int failExpiredCodeUploads() {
                called.set(true);
                return 1;
            }
        };
        FailExpiredLambdaCodeUploadsJob job = new FailExpiredLambdaCodeUploadsJob(service);

        job.run();

        assertEquals("FailExpiredLambdaCodeUploads", job.name());
        assertEquals("0/30 * * * * ?", job.cron());
        assertTrue(called.get(), "job should fail expired uploads directly");
    }

    @Test
    void dispatchExpiredLambdaRunCompletionsRunsEverySecond() {
        AtomicBoolean called = new AtomicBoolean(false);
        LambdaClientService service = (LambdaClientService) Proxy.newProxyInstance(
            LambdaClientService.class.getClassLoader(),
            new Class<?>[]{LambdaClientService.class},
            (proxy, method, args) -> {
                if (method.getName().equals("dispatchExpiredLambdaRunCompletions")) {
                    called.set(true);
                }
                return null;
            });
        DispatchExpiredLambdaRunCompletionsJob job = new DispatchExpiredLambdaRunCompletionsJob(service);

        job.run();

        assertEquals("DispatchExpiredLambdaRunCompletions", job.name());
        assertEquals("* * * * * ?", job.cron());
        assertTrue(called.get(), "job should dispatch expired lambda run completions directly");
    }

    @Test
    void schedulesAndFiresRepeatedly() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        ExecutorCronScheduler s = startScheduler();
        s.registerJob("exec-fire", latch::countDown, false);
        s.scheduleCronJob("exec-fire", EVERY_SECOND, TZ);

        assertTrue(latch.await(4, TimeUnit.SECONDS), "job should fire at least twice on a 1s cron");
    }

    @Test
    void rescheduleAppliesNewCron() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorCronScheduler s = startScheduler();
        s.registerJob("exec-resched", latch::countDown, false);
        s.scheduleCronJob("exec-resched", NEVER_SOON, TZ); // would not fire during the test
        s.scheduleCronJob("exec-resched", EVERY_SECOND, TZ); // reschedule to fire each second

        assertTrue(latch.await(4, TimeUnit.SECONDS), "reschedule to a 1s cron should make the job fire");
    }

    @Test
    void disallowConcurrentUsesSharedLockAcrossWorkers() throws Exception {
        InMemoryCronJobLockDao locks = new InMemoryCronJobLockDao();
        ExecutorCronScheduler first = new ExecutorCronScheduler(List.of(), locks, Duration.ofMinutes(1));
        ExecutorCronScheduler second = new ExecutorCronScheduler(List.of(), locks, Duration.ofMinutes(1));
        scheduler = first;
        first.start();
        second.start();

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        Runnable job = () -> {
            executions.incrementAndGet();
            entered.countDown();
            try {
                finish.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        first.registerJob("cluster-job", job, true);
        second.registerJob("cluster-job", job, true);

        Thread firstWorker = new Thread(
            () -> first.runJobOnce("cluster-job", new AtomicBoolean()), "first-test-worker");
        firstWorker.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS), "first worker should enter the job");

        second.runJobOnce("cluster-job", new AtomicBoolean());
        assertEquals(1, executions.get(), "second worker must not enter while the DB lock is held");

        finish.countDown();
        firstWorker.join(2_000);
        second.runJobOnce("cluster-job", new AtomicBoolean());
        assertEquals(2, executions.get(), "another worker should run after the lock is released");
        second.shutdown();
    }

    private static final class InMemoryCronJobLockDao implements CronJobLockDao {
        private String jobName;
        private String ownerId;
        private Timestamp lockUntil;

        @Override
        public synchronized boolean claimExpired(String requestedJobName, String requestedOwnerId,
            Timestamp now, Timestamp requestedLockUntil)
        {
            if (!requestedJobName.equals(jobName) || lockUntil.after(now)) return false;
            ownerId = requestedOwnerId;
            lockUntil = requestedLockUntil;
            return true;
        }

        @Override
        public synchronized void insert(String requestedJobName, String requestedOwnerId,
            Timestamp requestedLockUntil)
        {
            if (ownerId != null) throw new DuplicateKeyException("lock already exists");
            jobName = requestedJobName;
            ownerId = requestedOwnerId;
            lockUntil = requestedLockUntil;
        }

        @Override
        public synchronized boolean renew(String requestedJobName, String requestedOwnerId,
            Timestamp requestedLockUntil)
        {
            if (!requestedJobName.equals(jobName) || !requestedOwnerId.equals(ownerId)) return false;
            lockUntil = requestedLockUntil;
            return true;
        }

        @Override
        public synchronized boolean release(String requestedJobName, String requestedOwnerId) {
            if (!requestedJobName.equals(jobName) || !requestedOwnerId.equals(ownerId)) return false;
            jobName = null;
            ownerId = null;
            lockUntil = null;
            return true;
        }
    }

}
