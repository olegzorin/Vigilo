package dev.olegz.vf.worker.service;

import dev.olegz.vf.common.props.IntProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.cache.CacheInvalidationOutboxEntry;
import dev.olegz.vf.core.service.cache.CacheInvalidationOutboxService;
import dev.olegz.vf.messaging.ConfirmingMessageProducer;
import dev.olegz.vf.messaging.Messaging;
import dev.olegz.vf.messaging.MessagingProvider;
import dev.olegz.vf.messaging.Topics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CacheInvalidationOutboxDispatcher {
    private final CacheInvalidationOutboxService outboxService;
    private final ConfirmingMessageProducer producer;

    @Autowired
    public CacheInvalidationOutboxDispatcher(CacheInvalidationOutboxService outboxService) {
        this(outboxService, Messaging.producer(
            MessagingProvider.KAFKA, "cache-outbox", ConfirmingMessageProducer.class));
    }

    CacheInvalidationOutboxDispatcher(
        CacheInvalidationOutboxService outboxService,
        ConfirmingMessageProducer producer)
    {
        this.outboxService = outboxService;
        this.producer = producer;
    }

    public int dispatchPending() {
        int maxBatchSize = PropertyStore.getInt(IntProp.CACHE_INVALIDATION_OUTBOX_DISPATCH_BATCH_SIZE);
        int dispatched = 0;

        while (dispatched < maxBatchSize) {
            CacheInvalidationOutboxEntry entry = outboxService.claimNext();
            if (entry == null) break;

            try {
                producer.sendAndAwait(Topics.CACHE_INVALIDATION, entry.payload);
                if (!outboxService.completeClaim(entry)) {
                    throw new IllegalStateException("Cache invalidation claim changed before completion: id=" + entry.id);
                }
                dispatched++;
            } catch (Exception e) {
                try {
                    outboxService.releaseClaim(entry);
                } catch (Exception releaseError) {
                    e.addSuppressed(releaseError);
                }
                throw e;
            }
        }

        return dispatched;
    }
}
