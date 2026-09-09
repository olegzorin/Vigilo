package dev.olegz.vf.core.dao;

import java.sql.Timestamp;
import java.util.UUID;

import javax.sql.DataSource;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.core.TestConfiguration;
import dev.olegz.vf.core.dao.mapper.LambdaAssignmentCleanupMapper;
import dev.olegz.vf.core.dao.mapper.LambdaAsyncSubmissionOutboxMapper;
import dev.olegz.vf.core.dao.mapper.LambdaInvokeRetryOutboxMapper;
import dev.olegz.vf.core.dao.mapper.LambdaResetOutboxMapper;
import dev.olegz.vf.core.dao.mapper.LambdaRunCompletionOutboxMapper;
import dev.olegz.vf.core.dao.mapper.CacheInvalidationOutboxMapper;
import dev.olegz.vf.core.domain.lambdarun.LambdaAsyncSubmissionOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.LambdaInvokeRetryOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.LambdaResetOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunCompletionOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunContext;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.domain.cache.CacheInvalidationOutboxEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class ReturningOutboxDaoTest {
    private final LambdaAsyncSubmissionOutboxMapper asyncMapper;
    private final LambdaInvokeRetryOutboxMapper retryMapper;
    private final LambdaResetOutboxMapper resetMapper;
    private final LambdaRunCompletionOutboxMapper completionMapper;
    private final CacheInvalidationOutboxMapper cacheMapper;
    private final LambdaAssignmentCleanupMapper cleanupMapper;
    private final JdbcTemplate jdbc;

    @Autowired
    ReturningOutboxDaoTest(
        LambdaAsyncSubmissionOutboxMapper asyncMapper,
        LambdaInvokeRetryOutboxMapper retryMapper,
        LambdaResetOutboxMapper resetMapper,
        LambdaRunCompletionOutboxMapper completionMapper,
        CacheInvalidationOutboxMapper cacheMapper,
        LambdaAssignmentCleanupMapper cleanupMapper,
        DataSource dataSource)
    {
        this.asyncMapper = asyncMapper;
        this.retryMapper = retryMapper;
        this.resetMapper = resetMapper;
        this.completionMapper = completionMapper;
        this.cacheMapper = cacheMapper;
        this.cleanupMapper = cleanupMapper;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    @Test
    void claimsOutboxRowsAndTakesCleanupRowThroughReturningStatements() {
        clearQueues();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp due = new Timestamp(now.getTime() - 1000);
        Timestamp claimUntil = new Timestamp(now.getTime() + 60_000);

        LambdaAsyncSubmissionOutboxEntry async =
            new LambdaAsyncSubmissionOutboxEntry(101, 1001, new byte[]{1}, due, "failed");
        asyncMapper.insert(async);
        LambdaAsyncSubmissionOutboxEntry claimedAsync =
            asyncMapper.claimNextDue(now, claimId(), claimUntil);
        assertEquals(async.runId, claimedAsync.runId);
        assertClaimed(claimedAsync.claimId, claimedAsync.claimUntil, claimUntil);
        assertNull(asyncMapper.claimNextDue(now, claimId(), claimUntil));

        LambdaInvokeRetryOutboxEntry retry =
            new LambdaInvokeRetryOutboxEntry(102, InvocationLane.DEFAULT, 1002, 3, new byte[]{2}, due);
        retryMapper.insert(retry);
        LambdaInvokeRetryOutboxEntry claimedRetry = retryMapper.claimNextDue(now, claimId(), claimUntil);
        assertEquals(retry.runId, claimedRetry.runId);
        assertEquals(retry.lane, claimedRetry.lane);
        assertClaimed(claimedRetry.claimId, claimedRetry.claimUntil, claimUntil);

        LambdaResetOutboxEntry reset = new LambdaResetOutboxEntry(
            103, UUID.randomUUID().toString(), 1003, 104, 5, due);
        resetMapper.insert(reset);
        LambdaResetOutboxEntry claimedReset = resetMapper.claimNextAvailable(now, claimId(), claimUntil);
        assertEquals(reset.eventId, claimedReset.eventId);
        assertClaimed(claimedReset.claimId, claimedReset.claimUntil, claimUntil);

        LambdaRunContext context = new LambdaRunContext();
        context.lambdaAssignmentId = 105;
        context.lane = InvocationLane.ASYNC;
        context.requestId = 1004;
        completionMapper.insert(new LambdaRunCompletionOutboxEntry(context, due));
        LambdaRunCompletionOutboxEntry claimedCompletion =
            completionMapper.claimNextAvailable(now, claimId(), claimUntil);
        assertEquals(context.requestId, claimedCompletion.runId);
        assertEquals(context.lane, claimedCompletion.lane);
        assertClaimed(claimedCompletion.claimId, claimedCompletion.claimUntil, claimUntil);

        cacheMapper.insert(new CacheInvalidationOutboxEntry(new byte[]{3}, due));
        CacheInvalidationOutboxEntry claimedCache =
            cacheMapper.claimNextAvailable(now, claimId(), claimUntil);
        assertArrayEquals(new byte[]{3}, claimedCache.payload);
        assertClaimed(claimedCache.claimId, claimedCache.claimUntil, claimUntil);

        cleanupMapper.insertLambdaAssignmentCleanup(106, new Datetime(due.getTime()));
        assertEquals(106, cleanupMapper.takeNextLambdaAssignmentForCleanup(new Datetime(now.getTime())));
        assertNull(cleanupMapper.takeNextLambdaAssignmentForCleanup(new Datetime(now.getTime())));
    }

    private void clearQueues() {
        jdbc.update("DELETE FROM lambda_async_submission_outbox");
        jdbc.update("DELETE FROM lambda_invoke_retry_outbox");
        jdbc.update("DELETE FROM lambda_reset_outbox");
        jdbc.update("DELETE FROM lambda_run_completion_outbox");
        jdbc.update("DELETE FROM cache_invalidation_outbox");
        jdbc.update("DELETE FROM lambda_assignment_cleanup_queue");
    }

    private static String claimId() {
        return UUID.randomUUID().toString();
    }

    private static void assertClaimed(String claimId, Timestamp actualClaimUntil, Timestamp expectedClaimUntil) {
        assertNotNull(claimId);
        assertEquals(expectedClaimUntil, actualClaimUntil);
    }
}
