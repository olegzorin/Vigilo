package dev.olegz.vf.messaging;

import dev.olegz.vf.messaging.consumer.AckStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AckStatusTest {

    @Test
    void notAcknowledgedUntilMarked() {
        AckStatus ack = new AckStatus();
        assertFalse(ack.isAcknowledged(), "a fresh status must not be acknowledged");

        ack.acknowledge();
        assertTrue(ack.isAcknowledged(), "status must be acknowledged after acknowledge()");
    }

    @Test
    void acknowledgeIsIdempotent() {
        AckStatus ack = new AckStatus();
        ack.acknowledge();
        ack.acknowledge();
        assertTrue(ack.isAcknowledged());
    }
}
