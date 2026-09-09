package dev.olegz.vf.messaging;

import dev.olegz.vf.common.objectmap.BytesMapper;

/**
 * Broker-agnostic producer of byte-array records.
 * <p>
 * A record consists of a topic name and a value. Producers that support broker-native keyed routing implement
 * {@link KeyedMessageProducer}; callers that depend on key semantics should require that capability explicitly.
 */
public interface MessageProducer {

    /**
     * Asynchronously send a value to a topic.
     * @param topic topic name
     * @param value record value
     */
    void send(String topic, byte[] value);

    /**
     * Asynchronously send an object to a topic, serialized to bytes with {@link BytesMapper}.
     * @param topic topic name
     * @param value record value; serialized via {@link BytesMapper#writeValue(Object)}
     */
    default void send(String topic, Object value) {
        send(topic, BytesMapper.writeValue(value));
    }

}
