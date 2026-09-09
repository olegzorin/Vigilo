package dev.olegz.vf.common.props;

/**
 * Registry of integer properties that are read on hot paths, where re-parsing the raw string value on every
 * access would be wasteful.
 * <p>
 * Each constant pairs a property key with its {@code int} default. {@link PropertyStore} parses these once at
 * startup into an {@code EnumMap} and re-parses only when the backing property file changes, so
 * {@link PropertyStore#getInt(IntProp)} is a cheap map lookup rather than a {@code String}-to-{@code int}
 * conversion. This is the sole reason to add an entry here.
 * <p>
 * Do <b>not</b> add a property that is read once into a {@code static}/{@code final} field, or one read only at
 * startup — those are parsed a single time regardless, gain nothing from the cache, and are clearer kept inline
 * as {@link PropertyStore#getInt(String, int)} next to their use site. Only {@code int}-valued keys with a
 * literal default belong here; {@code long}-valued, computed-default, or dynamically-keyed properties cannot be
 * represented.
 */
public enum IntProp {

    // api
    API_MIN_ROW_COUNT("vf.api.minRowCount", 100),

    // lambdas
    LAMBDA_MEMORY_SIZE_MB("vf.lambda.memory", 1024),
    LAMBDA_ASYNC_MEMORY_SIZE_MB("vf.lambda.async.memory", 10240),
    LAMBDA_RETRIES_MAX("vf.lambda.retries.max", 5),
    LAMBDA_RETRIES_MAX_PENDING_EXECUTIONS("vf.lambda.retries.maxPendingRuns", 5),
    LAMBDA_RETRY_OUTBOX_DISPATCH_BATCH_SIZE("vf.lambda.retries.outbox.dispatchBatchSize", 100),
    LAMBDA_ASYNC_SUBMISSION_DISPATCH_BATCH_SIZE("vf.lambda.async.submission.dispatchBatchSize", 100),
    LAMBDA_COMPLETION_OUTBOX_DISPATCH_BATCH_SIZE("vf.lambda.completions.outbox.dispatchBatchSize", 100),
    LAMBDA_RESET_OUTBOX_DISPATCH_BATCH_SIZE("vf.lambda.resets.outbox.dispatchBatchSize", 100),
    CACHE_INVALIDATION_OUTBOX_DISPATCH_BATCH_SIZE("vf.cache.invalidation.outbox.dispatchBatchSize", 100),
    LAMBDA_MAX_TIMEOUTS_PER_HOUR("vf.lambda.maxTimeoutsPerHour", 60), // monitoring
    LOC_LAMBDA_MAX_INPUTS_PER_REQUEST("vf.lambda.loc.requests.maxBatchSize", 30),
    LAMBDA_MAX_PENDING_EXECUTIONS("vf.lambda.maxPendingRuns", 25),
    LAMBDA_MAX_INPUT_DATA_SIZE_BYTES("vf.lambda.maxInputDataSize", 500_000),
    LAMBDA_STATS_MAX_BATCH_SIZE("vf.lambda.maxStatsSize", 1000),
    LAMBDA_RUN_RESULTS_PARTITIONS_COUNT("vf.db.lambda_run_results.partitions", 8),
    LAMBDA_RUN_RESULTS_PARTITIONS_SHIFT("vf.db.lambda_run_results.partShift", 0),
    ;

    final String label;
    final int defaultValue;

    IntProp(String label, int defaultValue) {
        this.label = label;
        this.defaultValue = defaultValue;
    }
}
