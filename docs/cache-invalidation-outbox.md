# Cache Invalidation Transactional Outbox

The cache-invalidation outbox turns cross-JVM Caffeine invalidation into a durable, at-least-once pipeline:

```text
API database transaction
    -> update business data
    -> insert cache_invalidation_outbox row
    -> commit both atomically
    -> apply deferred local Caffeine eviction

Worker poller
    -> claim outbox row
    -> publish to Kafka and wait for broker acknowledgement
    -> delete claimed outbox row

Every API/worker JVM
    -> consume Kafka invalidation
    -> evict one key or clear the named local cache
```

## 1. A Database Write Requests Invalidation

Cache-affecting database writes have both `@Transactional` and `@CacheEvict`. For example, updating a location invalidates its runtime assignments and trigger metadata:

```java
@Transactional
@CacheEvict(
    cacheNames = {
        CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS,
        CacheNames.TRIGGER_LOCATION_METADATA
    },
    key = "#location.locationId")
public boolean updateLocation(Location location)
```

The implementation is in [`LocationDaoImpl`](../registry/src/main/java/dev/olegz/vf/registry/dao/impl/LocationDaoImpl.java).

Spring opens the transaction before invoking the cache interceptor because transaction management is configured with order `100`, outside the caching advice. The effective order is:

1. Begin the database transaction.
2. Execute the business `INSERT`, `UPDATE`, or `DELETE`.
3. A successful method return triggers `@CacheEvict`.
4. Cache interception inserts the outbox record.
5. Commit the business change and outbox record together.
6. Apply the local Caffeine eviction after commit.

If any step before commit fails, including insertion into the outbox, the complete transaction rolls back.

Every DAO method with `@CacheEvict` has its own public transaction boundary. This guarantees atomicity even when a caller does not already have an outer transaction.

## 2. The Cache Wrapper Creates the Event

[`CaffeineCacheInvalidationManager`](../core/src/main/java/dev/olegz/vf/core/cache/CaffeineCacheInvalidationManager.java) wraps caches requiring cross-JVM invalidation with `InvalidatingCache`. Read-only caches receive only the ordinary transaction-aware wrapper.

For an eviction, the wrapper first enqueues an event and then delegates the local mutation:

```java
public void evict(Object key) {
    enqueueEvent(key);
    super.evict(key);
}
```

The serialized event has this logical form:

```json
{
  "server": "<origin JVM UUID>",
  "cache": "triggerLocationMetadata",
  "key": 42
}
```

The fields are defined by [`CacheInvalidationEvent`](../core/src/main/java/dev/olegz/vf/core/cache/CacheInvalidationEvent.java):

- A non-null `key` means evict that particular entry.
- A null `key` means clear the entire named cache.
- `server` identifies the JVM that originated the invalidation.

For cache puts, an event is created only when the operation overwrites an entry already present locally. Populating a previously absent entry after a read does not invalidate other JVMs.

## 3. The Event Enters the Business Transaction

`CacheInvalidationOutboxService.enqueue()` uses the default `REQUIRED` transaction propagation:

```java
@Transactional
public void enqueue(byte[] payload)
```

It therefore joins the transaction already opened around the cache-evicting DAO method. It inserts the serialized event and creation time into `cache_invalidation_outbox`.

The table contains:

| Column | Purpose |
|---|---|
| `id` | Monotonically increasing event identity and polling order |
| `payload` | Serialized cache-invalidation event |
| `created_at` | Time the event was created |
| `claim_id` | UUID of the publisher currently owning the row |
| `claim_until` | Expiration time of that claim |

The schema is present in the canonical PostgreSQL DDL. Existing deployments must receive an
equivalent reviewed migration before the worker dispatcher is deployed.

At transaction completion there are only two valid outcomes:

| Outcome | Business change | Outbox event | Local eviction |
|---|---:|---:|---:|
| Commit | Yes | Yes | Applied after commit |
| Rollback | No | No | Not applied |

`TransactionAwareCacheDecorator` delays the local Caffeine mutation until commit. This prevents a rolled-back write from removing a valid local cached value.

## 4. The Worker Polls the Outbox

[`DispatchCacheInvalidationsJob`](../worker/src/main/java/dev/olegz/vf/worker/scheduler/job/DispatchCacheInvalidationsJob.java) runs once per second:

```java
return "* * * * * ?";
```

Each run calls [`CacheInvalidationOutboxDispatcher`](../worker/src/main/java/dev/olegz/vf/worker/service/CacheInvalidationOutboxDispatcher.java), which dispatches up to 100 records by default. The batch size is configurable through:

```properties
vf.cache.invalidation.outbox.dispatchBatchSize
```

The dispatcher repeatedly claims and publishes rows until it reaches the batch limit or finds no available row.

## 5. Claiming Supports Concurrent Workers

Claiming happens in an independent, short `REQUIRES_NEW` transaction:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public CacheInvalidationOutboxEntry claimNext()
```

The mapper selects the oldest unclaimed or expired row:

```sql
SELECT id, payload, created_at, claim_id, claim_until
FROM cache_invalidation_outbox
WHERE claim_until IS NULL OR claim_until <= :now
ORDER BY id
LIMIT 1
FOR UPDATE SKIP LOCKED
```

`FOR UPDATE SKIP LOCKED` provides safe concurrent polling:

- The selected row is locked while the claim is recorded.
- Another worker does not wait for that row.
- It can skip the locked row and claim another one.
- Multiple workers can publish concurrently without normally selecting the same event.

The service assigns a random UUID as `claim_id` and a lease expiration as `claim_until`. The default lease is two minutes and is configurable through:

```properties
vf.cache.invalidation.outbox.claimLease
```

The claim transaction commits before publication. This releases the database row lock while retaining logical ownership through `claim_id`.

## 6. Kafka Acknowledgement Is Required

The dispatcher publishes to `Topics.CACHE_INVALIDATION` using [`ConfirmingMessageProducer`](../messaging/src/main/java/dev/olegz/vf/messaging/ConfirmingMessageProducer.java):

```java
producer.sendAndAwait(Topics.CACHE_INVALIDATION, entry.payload);
```

Unlike the ordinary asynchronous producer, the Kafka implementation waits for the producer future to finish:

```java
producer.send(record).get();
```

This confirms that Kafka accepted the record according to the configured producer acknowledgement policy. It does not mean that every consumer has already processed it.

After acknowledgement, the dispatcher deletes the row using both `id` and `claim_id`. Including `claim_id` prevents an obsolete publisher from deleting a row whose claim has changed.

## 7. Publication Failures Are Retried

If Kafka publication fails:

1. `sendAndAwait()` throws.
2. The dispatcher clears `claim_id` and `claim_until`.
3. The scheduled job logs the failure.
4. The next polling run can claim the row again.

If the worker crashes without releasing its claim, `claim_until` eventually expires. Another worker can reclaim the event after the lease expires.

If Kafka acknowledges the record but the worker crashes before deleting the outbox row, the event will be published again. This is why the delivery guarantee is at least once rather than exactly once.

## 8. Every JVM Receives the Event

Each Caffeine manager registers [`CaffeineCacheInvalidator`](../core/src/main/java/dev/olegz/vf/core/cache/CaffeineCacheInvalidator.java) using `ConsumerGroupScope.PER_LISTENER`. API and worker JVMs therefore consume under independent Kafka groups and each receives every invalidation.

On receipt, the invalidator:

1. Deserializes the event.
2. Ignores it if its `server` equals the current JVM instance ID.
3. Locates the native Caffeine cache by name.
4. Invalidates the specified key, or all entries if the key is null.

The consumer mutates the native cache directly:

```java
if (event.key == null) {
    cache.invalidateAll();
} else {
    cache.invalidate(event.key);
}
```

Bypassing the outbox-aware wrapper is deliberate. Passing a received invalidation through that wrapper would create another outbox event and produce an infinite Kafka loop.

The originating JVM ignores its own Kafka event because its local eviction was already applied after the database commit. If that JVM restarted in the meantime, its instance UUID is different and processing the old event is harmless.

## Delivery Guarantees and Failure Windows

The implementation provides at-least-once invalidation delivery:

| Failure point | Result |
|---|---|
| Before database commit | Business change, outbox row, and local eviction are all rolled back |
| After commit but before polling | Durable outbox row remains for a later poll |
| After claiming but before publishing | Claim expires and the event becomes available again |
| Kafka rejects or fails the send | Claim is released immediately for retry |
| After Kafka acknowledgement but before row deletion | Event is published again; duplicate eviction is harmless |
| After row deletion | Publication is complete |

The mechanism does not provide instantaneous or synchronous cache coherence. Between the database commit and Kafka consumption, another JVM can briefly serve a previously cached value. Normally this window consists of the polling interval plus Kafka delivery time. If the worker or Kafka is unavailable, the window lasts until recovery.

It also does not replicate cached values. Kafka carries only instructions to discard local values. The next read reloads fresh data from the database.

## Operational Requirements

- Apply the database migration before deploying code that writes cache invalidations. If the outbox insert fails, the associated business transaction intentionally fails as well.
- Run at least one worker instance so committed outbox rows are published.
- Monitor outbox row count and oldest `created_at`; growth indicates that polling or Kafka publication is failing.
- Keep the cache-invalidation Kafka topic configured as a broadcast topic with per-listener consumption.
- Treat duplicate messages as expected. Cache eviction is idempotent, so no consumer-side deduplication is required.
