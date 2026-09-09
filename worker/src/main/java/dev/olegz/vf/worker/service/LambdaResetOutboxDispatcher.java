package dev.olegz.vf.worker.service;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.IntProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.LambdaResetOutboxEntry;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.service.lambda.LambdaResetOutboxService;
import dev.olegz.vf.messaging.ConfirmingKeyedMessageProducer;
import dev.olegz.vf.messaging.Messaging;
import dev.olegz.vf.messaging.MessagingProvider;
import dev.olegz.vf.messaging.Topics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LambdaResetOutboxDispatcher {
    private final LambdaResetOutboxService outboxService;
    private final ConfirmingKeyedMessageProducer producer;

    @Autowired
    public LambdaResetOutboxDispatcher(LambdaResetOutboxService outboxService) {
        this(outboxService, Messaging.producer(
            MessagingProvider.KAFKA, "lambda-reset-outbox", ConfirmingKeyedMessageProducer.class));
    }

    LambdaResetOutboxDispatcher(
        LambdaResetOutboxService outboxService,
        ConfirmingKeyedMessageProducer producer)
    {
        this.outboxService = outboxService;
        this.producer = producer;
    }

    public int dispatchPending() {
        int maxBatchSize = PropertyStore.getInt(IntProp.LAMBDA_RESET_OUTBOX_DISPATCH_BATCH_SIZE);
        int dispatched = 0;

        while (dispatched < maxBatchSize) {
            LambdaResetOutboxEntry entry = outboxService.claimNext();
            if (entry == null) break;

            try {
                producer.sendAndAwait(Topics.LAMBDA_RESET, entry.locationId, BytesMapper.writeValue(toEvent(entry)));
                if (!outboxService.completeClaim(entry)) {
                    throw new IllegalStateException("Lambda reset claim changed before completion: id=" + entry.id);
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

    private static ResetEvent toEvent(LambdaResetOutboxEntry entry) {
        ResetEvent event = new ResetEvent(entry.locationId, entry.lambdaAssignmentId);
        event.eventId = entry.eventId;
        event.time = entry.eventTime;
        event.variableGeneration = entry.variableGeneration;
        return event;
    }
}
