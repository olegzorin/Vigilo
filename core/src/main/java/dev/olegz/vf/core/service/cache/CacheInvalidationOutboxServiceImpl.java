package dev.olegz.vf.core.service.cache;

import java.sql.Timestamp;
import java.util.UUID;

import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.CacheInvalidationOutboxDao;
import dev.olegz.vf.core.domain.cache.CacheInvalidationOutboxEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CacheInvalidationOutboxServiceImpl implements CacheInvalidationOutboxService {
    private final CacheInvalidationOutboxDao outboxDao;

    public CacheInvalidationOutboxServiceImpl(CacheInvalidationOutboxDao outboxDao) {
        this.outboxDao = outboxDao;
    }

    @Override
    @Transactional
    public void enqueue(byte[] payload) {
        outboxDao.insert(new CacheInvalidationOutboxEntry(
            payload, new Timestamp(System.currentTimeMillis())));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CacheInvalidationOutboxEntry claimNext() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        var lease = PropertyStore.getDuration(DurationProp.CACHE_INVALIDATION_OUTBOX_CLAIM_LEASE);
        return outboxDao.claimNextAvailable(
            now, UUID.randomUUID().toString(), new Timestamp(now.getTime() + lease.toMillis()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeClaim(CacheInvalidationOutboxEntry entry) {
        return outboxDao.deleteClaimed(entry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releaseClaim(CacheInvalidationOutboxEntry entry) {
        return outboxDao.releaseClaim(entry);
    }
}
