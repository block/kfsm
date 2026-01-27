# kFSM jOOQ Module

This module provides production-ready utilities for the kFSM v2 transactional outbox pattern using jOOQ.

## Features

- **`JooqOutbox`**: A jOOQ-based `Outbox` implementation using `SELECT ... FOR UPDATE SKIP LOCKED`
- **`PollingEffectProcessor`**: Background processor with exponential backoff
- **`OutboxSchema`**: DDL for MySQL and PostgreSQL
- **`MoshiOutboxSerializer`**: Moshi-based effect serialization for sealed classes

## Installation

```kotlin
dependencies {
  implementation("app.cash.kfsm:lib-jooq:<version>")
}
```

## Quick Start

### 1. Create the outbox table

```kotlin
// Using the provided schema
dsl.execute(OutboxSchema.mysql())
```

Or manually create the table:

```sql
CREATE TABLE outbox (
  id VARCHAR(36) NOT NULL,
  value_id VARCHAR(255) NOT NULL,
  effect_type VARCHAR(255) NOT NULL,
  effect_payload TEXT NOT NULL,
  dedup_key VARCHAR(255) NULL,
  depends_on_effect_id VARCHAR(36) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  last_error TEXT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  processed_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id),
  INDEX idx_outbox_pending (status, created_at),
  INDEX idx_outbox_value_id (value_id, status, created_at)
);
```

### 2. Define your serializer

```kotlin
// For sealed class effects with String IDs
val serializer = MoshiOutboxSerializer.forSealedClassWithStringId(OrderEffect::class)

// For custom ID types
val serializer = MoshiOutboxSerializer.forSealedClass(
  sealedClass = OrderEffect::class,
  idSerializer = { it.toString() },
  idDeserializer = { UUID.fromString(it) }
)
```

### 3. Create the outbox

```kotlin
val outbox = JooqOutbox<String, OrderEffect>(
  dsl = dslContext,
  serializer = serializer
)
```

### 4. Start the background processor

```kotlin
val polling = PollingEffectProcessor(
  outbox = outbox,
  handler = orderEffectHandler,
  stateMachine = orderStateMachine,
  valueLoader = { id -> orderRepository.findById(id) },
  config = PollingConfig(
    baseInterval = Duration.ofMillis(100),
    maxInterval = Duration.ofSeconds(5),
    batchSize = 50
  )
)

polling.start()

// On shutdown
polling.stop()
```

### 5. Trigger immediate processing (optional)

For lower latency, trigger processing after successful transitions:

```kotlin
class OrderService(
  private val stateMachine: StateMachine<...>,
  private val polling: PollingEffectProcessor<...>
) {
  fun confirmOrder(order: Order): Result<Order> {
    return stateMachine.transition(order, ConfirmOrder())
      .onSuccess { polling.triggerNow() }
  }
}
```

## How SKIP LOCKED Works

This implementation uses `SELECT ... FOR UPDATE SKIP LOCKED` to enable concurrent processing across multiple application instances without blocking:

1. When fetching pending messages, we first claim a single `value_id` with SKIP LOCKED
2. Then we fetch all pending messages for that entity
3. This ensures:
   - Different instances process different entities in parallel
   - All effects for one entity are processed in order by the same instance
   - No lock contention or blocking between instances

## Maintenance

### Retry failed messages

```kotlin
// Retry messages that failed fewer than 3 times
val retried = outbox.retryFailed(maxAttempts = 3)
```

### Clean up old messages

```kotlin
// Delete messages processed more than 7 days ago
val deleted = outbox.deleteProcessed(olderThan = Instant.now().minus(7, ChronoUnit.DAYS))
```

### Monitor status

```kotlin
val counts = outbox.getStatusCounts()
// Map(PENDING -> 5, PROCESSED -> 1000, FAILED -> 2)
```

## Configuration

### PollingConfig options

| Option | Default | Description |
|--------|---------|-------------|
| `baseInterval` | 100ms | Polling interval when work is available |
| `maxInterval` | 5s | Maximum interval after backoff |
| `backoffMultiplier` | 2.0 | Exponential backoff multiplier |
| `batchSize` | 100 | Max messages per poll |
| `errorHandler` | null | Callback for processing errors |
