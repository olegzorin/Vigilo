package dev.olegz.vf.core.service.lambda;

import java.sql.Timestamp;
import java.util.UUID;

import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaResetOutboxDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaResetOutboxEntry;
import dev.olegz.vf.core.event.ResetEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LambdaResetOutboxServiceImpl implements LambdaResetOutboxService {
    private final LambdaResetOutboxDao outboxDao;

    public LambdaResetOutboxServiceImpl(LambdaResetOutboxDao outboxDao) {
        this.outboxDao = outboxDao;
    }

    @Override
    @Transactional
    public void enqueue(ResetEvent event) {
        outboxDao.insert(new LambdaResetOutboxEntry(
            event.locationId,
            event.eventId,
            event.time,
            event.lambdaAssignmentId,
            event.variableGeneration,
            new Timestamp(System.currentTimeMillis())));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LambdaResetOutboxEntry claimNext() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        var lease = PropertyStore.getDuration(DurationProp.LAMBDA_RESET_OUTBOX_CLAIM_LEASE);
        return outboxDao.claimNextAvailable(
            now, UUID.randomUUID().toString(), new Timestamp(now.getTime() + lease.toMillis()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeClaim(LambdaResetOutboxEntry entry) {
        return outboxDao.deleteClaimed(entry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releaseClaim(LambdaResetOutboxEntry entry) {
        return outboxDao.releaseClaim(entry);
    }
}
