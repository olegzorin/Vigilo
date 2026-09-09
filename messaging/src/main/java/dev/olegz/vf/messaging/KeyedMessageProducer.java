package dev.olegz.vf.messaging;

import dev.olegz.vf.common.objectmap.BytesMapper;

/**
 * Broker-agnostic producer of byte-array records.
 * <p>
 * A record consists of a topic name and a value. Producers that support broker-native keyed routing implement
 * {@link KeyedMessageProducer}; callers that depend on key semantics should require that capability explicitly.
 */
public interface KeyedMessageProducer extends MessageProducer {

    /**
     * Asynchronously send a keyed value to a topic.
     * @param topic topic name
     * @param key   record key
     * @param value record value
     */
    void send(String topic, String key, byte[] value);

    /**
     * Asynchronously send a keyed object to a topic, serialized to bytes with {@link BytesMapper}.
     * @param topic topic name
     * @param key   record key
     * @param value record value; serialized via {@link BytesMapper#writeValue(Object)}
     */
    default void send(String topic, String key, Object value) {
        send(topic, key, BytesMapper.writeValue(value));
    }

    /**
     * Asynchronously send a keyed value to a topic.
     * @param topic topic name
     * @param key   record key
     * @param value record value
     */
    default void send(String topic, int key, byte[] value) {
        send(topic, Integer.toString(key), value);
    };
}
