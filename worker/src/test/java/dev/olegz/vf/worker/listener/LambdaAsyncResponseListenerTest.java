package dev.olegz.vf.worker.listener;

import java.nio.charset.StandardCharsets;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.messaging.consumer.AckStatus;
import dev.olegz.vf.worker.domain.LambdaInvokeRequest;
import dev.olegz.vf.worker.domain.LambdaOutput;
import dev.olegz.vf.worker.service.LambdaFunctionInvoker;
import dev.olegz.vf.worker.service.LambdaRunService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaAsyncResponseListenerTest {
    private static final byte[] VALID_RESULT = ("""
        {
          "requestContext": {
            "requestId": "aws-request-id",
            "functionArn": "arn:aws:lambda:us-east-1:123456789012:function:test-function:1"
          },
          "requestPayload": {
            "id": 101,
            "lane": "ASYNC",
            "lambdaId": 7,
            "lambdaVersionId": 31,
            "runId": 1750000000000
          },
          "responseContext": {"statusCode": 200},
          "responsePayload": {"startCode": 0}
        }
        """).getBytes(StandardCharsets.UTF_8);

    @BeforeAll
    static void oneTimeSetUp() {
        PropertyStore.start();
    }

    @Test
    void acknowledgesOnlySuccessfullyProcessedResult() {
        RecordingLambdaRunService service = new RecordingLambdaRunService();
        LambdaAsyncResponseListener listener = new LambdaAsyncResponseListener(service);
        AckStatus ack = new AckStatus();

        try {
            listener.onMessage(VALID_RESULT, ack);
        } finally {
            service.shutdown();
        }

        assertTrue(ack.isAcknowledged());
        assertTrue(service.processed);
        assertEquals(0L, service.output.duration());
    }

    @Test
    void leavesFailedResultUnacknowledgedForRedelivery() {
        RecordingLambdaRunService service = new RecordingLambdaRunService();
        service.failure = new RuntimeException("database unavailable");
        LambdaAsyncResponseListener listener = new LambdaAsyncResponseListener(service);
        AckStatus ack = new AckStatus();

        try {
            assertThrows(RuntimeException.class, () -> listener.onMessage(VALID_RESULT, ack));
        } finally {
            service.shutdown();
        }

        assertFalse(ack.isAcknowledged());
    }

    @Test
    void leavesMalformedResultUnacknowledgedForRedelivery() {
        RecordingLambdaRunService service = new RecordingLambdaRunService();
        LambdaAsyncResponseListener listener = new LambdaAsyncResponseListener(service);
        AckStatus ack = new AckStatus();

        try {
            assertThrows(IllegalArgumentException.class,
                () -> listener.onMessage("not-json".getBytes(StandardCharsets.UTF_8), ack));
        } finally {
            service.shutdown();
        }

        assertFalse(ack.isAcknowledged());
    }

    private static final class RecordingLambdaRunService extends LambdaRunService {
        private boolean processed;
        private RuntimeException failure;
        private LambdaOutput output;

        private RecordingLambdaRunService() {
            super(null, null, null, new LambdaFunctionInvoker());
        }

        @Override
        public void processAsyncResult(LambdaInvokeRequest request, LambdaOutput output) {
            processed = true;
            this.output = output;
            if (failure != null) throw failure;
        }
    }
}
