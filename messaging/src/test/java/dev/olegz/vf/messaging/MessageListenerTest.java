package dev.olegz.vf.messaging;

import java.util.concurrent.atomic.AtomicReference;

import dev.olegz.vf.messaging.consumer.AckStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageListenerTest {

    @Test
    void defaultsAreNeutral() {
        MessageListener listener = message -> { };
        assertTrue(listener.commit(System.currentTimeMillis()), "commit() should default to true");
        assertEquals(0L, listener.messageTtlMillis(), "messageTtlMillis() should default to 0 (never expire)");
    }

    @Test
    void zeroTtlNeverExpires() {
        MessageListener listener = message -> { };
        // Even an ancient timestamp must not be treated as expired when no TTL is set.
        assertFalse(listener.expired(0L), "with TTL=0 no message should expire");
    }

    @Test
    void expiresOlderThanTtlButNotFreshMessages() {
        long ttl = 60_000L;
        MessageListener listener = new MessageListener() {
            @Override public void onMessage(byte[] message) { }
            @Override public long messageTtlMillis() { return ttl; }
        };

        long now = System.currentTimeMillis();
        // Older than TTL + slack -> expired; subtract a generous margin to stay clear of EXPIRY_SLACK_MS.
        assertTrue(listener.expired(now - ttl - MessageListener.EXPIRY_SLACK_MS - 10_000L),
            "a message older than its TTL must be expired");
        assertFalse(listener.expired(now), "a fresh message must not be expired");
    }

    @Test
    void asyncDefaultDelegatesAndAcknowledges() {
        AtomicReference<byte[]> received = new AtomicReference<>();
        MessageListener listener = received::set;

        AckStatus ack = new AckStatus();
        byte[] payload = {1, 2, 3};
        listener.onMessage(payload, ack);

        assertArrayEquals(payload, received.get(), "single-arg onMessage must be invoked");
        assertTrue(ack.isAcknowledged(), "message must be acknowledged after default async processing");
    }

    @Test
    void asyncDefaultLeavesFailureUnacknowledged() {
        MessageListener failing = message -> {
            throw new RuntimeException("boom");
        };

        AckStatus ack = new AckStatus();
        assertThrows(RuntimeException.class, () -> failing.onMessage(new byte[0], ack));
        assertFalse(ack.isAcknowledged(), "failed processing must leave the message available for retry");
    }
}
