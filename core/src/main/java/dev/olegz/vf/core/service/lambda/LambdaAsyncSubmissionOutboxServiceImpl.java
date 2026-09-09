package dev.olegz.vf.core.service.lambda;

import java.sql.Timestamp;
import java.util.UUID;

import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaAsyncSubmissionOutboxDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaAsyncSubmissionOutboxEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LambdaAsyncSubmissionOutboxServiceImpl implements LambdaAsyncSubmissionOutboxService {
    private final LambdaAsyncSubmissionOutboxDao outboxDao;

    public LambdaAsyncSubmissionOutboxServiceImpl(LambdaAsyncSubmissionOutboxDao outboxDao) {
        this.outboxDao = outboxDao;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(int lambdaAssignmentId, long runId, byte[] payload, Throwable failure) {
        Timestamp retryAt = retryAt(0);
        LambdaAsyncSubmissionOutboxEntry entry = new LambdaAsyncSubmissionOutboxEntry(
            lambdaAssignmentId, runId, payload, retryAt, failureMessage(failure));
        if (!outboxDao.exists(entry)) outboxDao.insert(entry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LambdaAsyncSubmissionOutboxEntry claimNextDue() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        var lease = PropertyStore.getDuration(DurationProp.LAMBDA_ASYNC_SUBMISSION_CLAIM_LEASE);
        return outboxDao.claimNextDue(
            now, UUID.randomUUID().toString(), new Timestamp(now.getTime() + lease.toMillis()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeClaim(LambdaAsyncSubmissionOutboxEntry entry) {
        return outboxDao.deleteClaimed(entry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean rescheduleClaim(LambdaAsyncSubmissionOutboxEntry entry, Throwable failure) {
        entry.retryAt = retryAt(entry.attemptCount + 1);
        entry.lastError = failureMessage(failure);
        return outboxDao.rescheduleClaimed(entry);
    }

    private static Timestamp retryAt(int attemptCount) {
        long initialDelay =
            PropertyStore.getDuration(DurationProp.LAMBDA_ASYNC_SUBMISSION_RETRY_DELAY).toMillis();
        long maxDelay =
            PropertyStore.getDuration(DurationProp.LAMBDA_ASYNC_SUBMISSION_RETRY_MAX_DELAY).toMillis();
        long multiplier = 1L << Math.min(attemptCount, 20);
        long delay = initialDelay > maxDelay / multiplier ? maxDelay : initialDelay * multiplier;
        return new Timestamp(System.currentTimeMillis() + delay);
    }

    private static String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return message != null ? message : failure.getClass().getName();
    }
}
