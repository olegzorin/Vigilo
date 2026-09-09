package dev.olegz.vf.core.service.lambda;

import java.sql.Timestamp;
import java.util.UUID;

import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaInvokeRetryOutboxDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaInvokeRetryOutboxEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LambdaInvokeRetryOutboxServiceImpl implements LambdaInvokeRetryOutboxService {
    private final LambdaInvokeRetryOutboxDao outboxDao;

    public LambdaInvokeRetryOutboxServiceImpl(LambdaInvokeRetryOutboxDao outboxDao) {
        this.outboxDao = outboxDao;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LambdaInvokeRetryOutboxEntry claimNextDue() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        var lease = PropertyStore.getDuration(DurationProp.LAMBDA_RETRY_OUTBOX_CLAIM_LEASE);
        return outboxDao.claimNextDue(
            now, UUID.randomUUID().toString(), new Timestamp(now.getTime() + lease.toMillis()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renewClaim(LambdaInvokeRetryOutboxEntry entry) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        var lease = PropertyStore.getDuration(DurationProp.LAMBDA_RETRY_OUTBOX_CLAIM_LEASE);
        entry.claimUntil = new Timestamp(now.getTime() + lease.toMillis());
        return outboxDao.renewClaim(entry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeClaim(LambdaInvokeRetryOutboxEntry entry) {
        return outboxDao.deleteClaimed(entry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releaseClaim(LambdaInvokeRetryOutboxEntry entry) {
        return outboxDao.releaseClaim(entry);
    }
}
