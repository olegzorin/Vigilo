package dev.olegz.vf.messaging;

import java.util.Set;

import dev.olegz.vf.messaging.consumer.ConsumerGroupScope;

/**
 * Message topic names.
 * <p>
 * The string values are the wire-level topic names and must match the broker configuration
 * (for Kafka: config/Kafka/config.sh and the patch script config/Kafka/topics_[version].sh).
 * New topics must be added there as well as here.
 */
public final class Topics {

    private Topics() {
    }

    //****************************************************************
    // Lambda execution
    //****************************************************************
    /** Lambda input */
    public static final String LAMBDA_INPUT = "lambda-input";
    /** Lambda assignment reset input */
    public static final String LAMBDA_RESET = "lambda-reset";
    /** Lambda assignment scheduled input */
    public static final String LAMBDA_SCHEDULE = "lambda-schedule";
    /** Default-lane lambda invocation request */
    public static final String LAMBDA_INVOKE_REQUEST = "lambda-invoke-request";
    /** Default-lane lambda run completion */
    public static final String LAMBDA_RUN_COMPLETION = "lambda-run-completion";

    /** Lambda execution errors and notifications */
    public static final String LAMBDA_ERROR = "lambda-error";
    /** Lambda assignment logs */
    public static final String LAMBDA_ASSIGNMENT_LOG = "lambda-assignment-log";
    /** Lambda code upload, single listener */
    public static final String LAMBDA_CODE_UPLOAD = "lambda-code";

    /** Asynchronous persistent operations */
    public static final String OPERATIONS = "operations";

    /** Asynchronous long-running lambda requests */
    public static final String LASTING_LAMBDA = "lasting-lambda";

    /** Cache invalidation topic with a consumer group for each listener instance. */
    public static final String CACHE_INVALIDATION = "cache-invalidation";

    /**
     * Topics broadcast to every node &mdash; each node consumes under its own group
     * ({@link ConsumerGroupScope#PER_SERVER}), so every node receives every record &mdash; as opposed to shared
     * work-queue topics consumed once per cluster ({@link ConsumerGroupScope#SHARED}).
     * <p>
     * This is a property of the topic itself: a topic is either a per-server broadcast or a cluster-shared queue,
     * never both, and producers and consumers must agree on it. A producer has no consumer-side {@link ConsumerGroupScope}
     * to consult, so it relies on this declaration to route correctly (the Artemis broker maps broadcast topics to
     * MULTICAST and shared topics to ANYCAST). New per-server topics must be listed here.
     */
    private static final Set<String> BROADCAST_TOPICS = Set.of(
        CACHE_INVALIDATION);

    /**
     * Whether {@code topic} is a per-server broadcast (every server gets every record) rather than a cluster-shared
     * work queue. See {@link #BROADCAST_TOPICS}.
     */
    public static boolean isBroadcast(String topic) {
        return BROADCAST_TOPICS.contains(topic);
    }
}
