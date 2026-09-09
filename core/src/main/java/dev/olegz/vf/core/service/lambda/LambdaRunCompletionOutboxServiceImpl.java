package dev.olegz.vf.core.service.lambda;

import java.sql.Timestamp;
import java.util.UUID;

import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaRunCompletionOutboxDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunCompletionOutboxEntry;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LambdaRunCompletionOutboxServiceImpl implements LambdaRunCompletionOutboxService {
    private final LambdaRunCompletionOutboxDao outboxDao;

    public LambdaRunCompletionOutboxServiceImpl(LambdaRunCompletionOutboxDao outboxDao) {
        this.outboxDao = outboxDao;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(LambdaRunContext context) {
        LambdaRunCompletionOutboxEntry entry = new LambdaRunCompletionOutboxEntry(
            context, new Timestamp(System.currentTimeMillis()));
        outboxDao.insert(entry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LambdaRunCompletionOutboxEntry claimNextAvailable() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        var lease = PropertyStore.getDuration(DurationProp.LAMBDA_COMPLETION_OUTBOX_CLAIM_LEASE);
        return outboxDao.claimNextAvailable(
            now, UUID.randomUUID().toString(), new Timestamp(now.getTime() + lease.toMillis()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renewClaim(LambdaRunCompletionOutboxEntry entry) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        var lease = PropertyStore.getDuration(DurationProp.LAMBDA_COMPLETION_OUTBOX_CLAIM_LEASE);
        entry.claimUntil = new Timestamp(now.getTime() + lease.toMillis());
        return outboxDao.renewClaim(entry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeClaim(LambdaRunCompletionOutboxEntry entry) {
        return outboxDao.deleteClaimed(entry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releaseClaim(LambdaRunCompletionOutboxEntry entry) {
        return outboxDao.releaseClaim(entry);
    }
}
