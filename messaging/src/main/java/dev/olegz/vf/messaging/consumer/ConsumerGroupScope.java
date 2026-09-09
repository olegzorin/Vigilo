package dev.olegz.vf.messaging.consumer;

/**
 * How a topic's records are shared across the nodes and JVM instances of a cluster,
 * determined by the consumer group id each listener registers under.
 */
public enum ConsumerGroupScope {
    /**
     * All JVM instances join a single consumer group (id derived from the topic alone).
     * The broker divides the topic's partitions among them, so each record
     * is delivered to exactly one instance -- the work is balanced across
     * the cluster. This is the usual choice for processing a stream once.
     */
    SHARED,

    /**
     * Each node consumes under its own group (id prefixed with the stable node id),
     * so every node owns all partitions independently and receives every record.
     * Use this when each node must react to the record on its own --
     * e.g. cache-invalidation broadcasts or per-node scheduled tasks.
     */
    PER_SERVER,

    /**
     * Each <em>listener registration</em> consumes under its own group
     * (id prefixed with the JVM instance id and made unique per registration),
     * so every listener receives every record independently --
     * even several listeners of the same topic on the same node.
     * The broker generates the unique group id; callers do not supply one.
     * Use this when a single node runs more than one listener that must
     * each see the full stream, e.g. multiple cache invalidators sharing a topic.
     */
    PER_LISTENER
}
