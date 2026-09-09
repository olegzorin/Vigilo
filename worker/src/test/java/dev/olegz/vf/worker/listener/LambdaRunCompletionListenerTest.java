package dev.olegz.vf.worker.listener;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunContext;
import dev.olegz.vf.messaging.RetryableMessageException;
import dev.olegz.vf.worker.service.LambdaFunctionInvoker;
import dev.olegz.vf.worker.service.LambdaRunService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LambdaRunCompletionListenerTest {
    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void continuationPublicationFailureRequestsKafkaRedelivery() {
        RuntimeException failure = new RuntimeException("database unavailable");
        LambdaRunService runService = new LambdaRunService(null, null, null, new LambdaFunctionInvoker()) {
            @Override public void processRunCompletion(LambdaRunContext completed) { throw failure; }
        };
        LambdaRunCompletionListener listener = new LambdaRunCompletionListener(runService);

        try {
            RetryableMessageException thrown = assertThrows(
                RetryableMessageException.class,
                () -> listener.onMessage(BytesMapper.writeValue(new LambdaRunContext())));
            assertSame(failure, thrown.getCause());
        } finally {
            runService.shutdown();
        }
    }
}
