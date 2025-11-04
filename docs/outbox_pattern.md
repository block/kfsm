# Transactional Outbox Pattern

kFSM supports the transactional outbox pattern, enabling reliable execution of side effects in distributed systems. This pattern ensures that state changes and their associated effects are persisted atomically, preventing lost updates even in the face of system failures.

## What is the Transactional Outbox Pattern?

The transactional outbox pattern solves a common problem in distributed systems: how to reliably execute side effects (like sending emails, publishing events, or calling external APIs) when a state change occurs, without risking data inconsistency if the system fails between persisting the state and executing the effect.

Traditional approaches have issues:
- **Execute effect then persist state**: If persistence fails, the effect cannot be rolled back
- **Persist state then execute effect**: If effect execution fails, the state is updated but the effect never happens
- **Two-phase commit**: Complex, slow, and not supported by many systems

The outbox pattern solves this by:
1. Capturing effects during the transition
2. Persisting both state and effects in a single database transaction
3. Processing effects asynchronously from the outbox

This guarantees **at-least-once delivery** semantics for effects.

## Key Components

kFSM provides the core interfaces for implementing the transactional outbox pattern. The actual processing of outbox messages (polling, execution, retry logic) is left to your application to implement based on your specific requirements.

### 1. DeferrableEffect

Transitions that implement `DeferrableEffect` can have their effects captured and stored in the outbox:

```kotlin
class SendEmail(
    from: OrderState,
    to: OrderState,
    private val recipient: String
) : OrderTransition(from, to), DeferrableEffect<String, Order, OrderState> {

    // The effect logic (executed later by your outbox processor)
    override fun effect(value: Order): Result<Order> {
        emailService.send(recipient, "Order ${value.id} is now ${to}")
        return Result.success(value)
    }

    // Serialize for storage in the outbox
    override fun serialize(value: Order): Result<EffectPayload> = Result.success(
        EffectPayload(
            effectType = "send_email",
            data = Json.encodeToString(mapOf(
                "recipient" to recipient,
                "orderId" to value.id,
                "newState" to to.toString()
            ))
        )
    )

    override val effectType = "send_email"
}
```

### 2. OutboxHandler

Captures effects during transitions. You implement this interface to define how effects are captured and persisted:

```kotlin
class DatabaseOutboxHandler<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
    private val database: Database
) : OutboxHandler<ID, V, S> {

    private val pendingMessages = mutableListOf<OutboxMessage<ID>>()

    override fun captureEffect(
        value: V,
        effect: DeferrableEffect<ID, V, S>
    ): Result<V> {
        val payload = effect.serialize(value).getOrElse { return Result.failure(it) }
        val message = OutboxMessage(
            id = UUID.randomUUID().toString(),
            valueId = value.id,
            effectPayload = payload,
            createdAt = System.currentTimeMillis()
        )
        pendingMessages.add(message)
        return Result.success(value)
    }

    override fun getPendingMessages(): List<OutboxMessage<ID>> =
        pendingMessages.toList()

    override fun clearPending() {
        pendingMessages.clear()
    }
}
```

### 3. Transitioner with Outbox

Configure your transitioner to use the outbox handler and persist effects atomically:

```kotlin
class OrderTransitioner(
    private val database: Database
) : Transitioner<String, OrderTransition, Order, OrderState>() {

    // Enable outbox pattern
    override val outboxHandler = DatabaseOutboxHandler(database)

    // Persist state and effects in a single transaction
    override fun persistWithOutbox(
        from: OrderState,
        value: Order,
        via: OrderTransition,
        outboxMessages: List<OutboxMessage<String>>
    ): Result<Order> = runCatching {
        database.transaction {
            // 1. Save the state change
            database.updateOrder(value).getOrThrow()

            // 2. Save all captured effects in the outbox table
            outboxMessages.forEach { message ->
                database.insertOutbox(message)
            }

            value
        }
    }
}
```

## Implementing Message Processing

kFSM does not include message processing infrastructure. Your application is responsible for:

1. **Polling the outbox table** for pending messages
2. **Deserializing and executing effects** based on the `EffectPayload`
3. **Updating message status** (PROCESSING, COMPLETED, FAILED)
4. **Implementing retry logic** with exponential backoff
5. **Handling dead letter queues** for permanently failed messages

Here's an example of how you might implement a simple processor:

```kotlin
class OrderEffectProcessor(
    private val database: Database,
    private val emailService: EmailService,
    private val notificationService: NotificationService
) {
    suspend fun processPending() {
        val pending = database.getPendingOutboxMessages(limit = 100)

        pending.forEach { message ->
            try {
                database.updateMessageStatus(message.id, OutboxStatus.PROCESSING)

                // Deserialize and execute the effect
                val data = Json.decodeFromString<Map<String, String>>(message.effectPayload.data)
                when (message.effectPayload.effectType) {
                    "send_email" -> {
                        val recipient = data["recipient"]!!
                        val orderId = data["orderId"]!!
                        emailService.send(recipient, "Order $orderId updated")
                    }
                    "send_notification" -> {
                        val userId = data["userId"]!!
                        val msg = data["message"]!!
                        notificationService.notify(userId, msg)
                    }
                    else -> error("Unknown effect type: ${message.effectPayload.effectType}")
                }

                database.updateMessageStatus(message.id, OutboxStatus.COMPLETED)
            } catch (e: Exception) {
                val newAttemptCount = message.attemptCount + 1
                if (newAttemptCount >= MAX_RETRIES) {
                    database.updateMessageStatus(
                        message.id,
                        OutboxStatus.FAILED,
                        lastError = e.message
                    )
                } else {
                    database.updateMessageForRetry(
                        message.id,
                        attemptCount = newAttemptCount,
                        lastError = e.message
                    )
                }
            }
        }
    }

    companion object {
        const val MAX_RETRIES = 3
    }
}

// In a background job or scheduled task
scope.launch {
    while (isActive) {
        processor.processPending()
        delay(1000) // Poll every second
    }
}
```

## Complete Example

Here's a complete example using a synchronous transitioner:

```kotlin
// States
sealed class OrderState(to: () -> Set<OrderState>) : State<String, Order, OrderState>(to)
data object Draft : OrderState({ setOf(Submitted) })
data object Submitted : OrderState({ setOf(Confirmed, Cancelled) })
data object Confirmed : OrderState({ setOf(Shipped) })
data object Shipped : OrderState({ setOf(Delivered) })
data object Delivered : OrderState({ emptySet() })
data object Cancelled : OrderState({ emptySet() })

// Value
data class Order(
    override val state: OrderState,
    override val id: String,
    val items: List<String>
) : Value<String, Order, OrderState> {
    override fun update(newState: OrderState): Order = copy(state = newState)
}

// Transition with deferrable effect
class SubmitOrder(
    private val customerId: String
) : OrderTransition(from = Draft, to = Submitted), DeferrableEffect<String, Order, OrderState> {

    override fun effect(value: Order): Result<Order> {
        emailService.sendConfirmation(customerId, value.id)
        return Result.success(value)
    }

    override fun serialize(value: Order): Result<EffectPayload> = Result.success(
        EffectPayload(
            effectType = "send_confirmation_email",
            data = """{"customerId":"$customerId","orderId":"${value.id}"}"""
        )
    )

    override val effectType = "send_confirmation_email"
}

// Transitioner
class OrderTransitioner(database: Database) :
    Transitioner<String, OrderTransition, Order, OrderState>() {

    override val outboxHandler = DatabaseOutboxHandler(database)

    override fun persistWithOutbox(
        from: OrderState,
        value: Order,
        via: OrderTransition,
        outboxMessages: List<OutboxMessage<String>>
    ): Result<Order> = database.transaction {
        database.save(value)
        outboxMessages.forEach { database.saveOutbox(it) }
        Result.success(value)
    }
}

// Usage
fun main() {
    val database = Database()
    val transitioner = OrderTransitioner(database)

    // Create and submit an order
    val draft = Order(state = Draft, id = "order-123", items = listOf("item1"))
    val submitted = transitioner.transition(draft, SubmitOrder("customer-456")).getOrThrow()

    // State is persisted, effect is captured in outbox (not sent yet)
    println("Order state: ${submitted.state}") // Submitted

    // Background processor executes effects (you implement this)
    val processor = OrderEffectProcessor(
        database = database,
        emailService = emailService,
        notificationService = notificationService
    )

    runBlocking {
        processor.processPending() // Email sent now
    }
}
```

## Database Schema

You'll need an outbox table to store pending effects. Here's an example schema:

```sql
CREATE TABLE outbox (
    id VARCHAR(255) PRIMARY KEY,
    value_id VARCHAR(255) NOT NULL,
    effect_type VARCHAR(100) NOT NULL,
    effect_data TEXT NOT NULL,
    effect_metadata TEXT,
    created_at BIGINT NOT NULL,
    processed_at BIGINT,
    status VARCHAR(20) NOT NULL,
    attempt_count INT DEFAULT 0,
    last_error TEXT,
    INDEX idx_status_created (status, created_at)
);
```

## Best Practices

### 1. Effect Idempotency

Since the outbox pattern provides **at-least-once** delivery, effects should be idempotent:

```kotlin
class SendEmail(...) : DeferrableEffect<...> {
    override fun effect(value: Order): Result<Order> {
        // Include a unique identifier to prevent duplicate sends
        val messageId = "order-${value.id}-state-${to}"
        emailService.sendIdempotent(messageId, recipient, subject, body)
        return Result.success(value)
    }
}
```

### 2. Error Handling

Implement retry logic with exponential backoff in your processor:

```kotlin
class OrderEffectProcessor(...) {
    suspend fun processPending() {
        val pending = database.getPendingOutboxMessages(limit = 100)

        pending.forEach { message ->
            try {
                // Calculate exponential backoff delay
                val delayMs = calculateBackoff(message.attemptCount)
                if (System.currentTimeMillis() - message.createdAt < delayMs) {
                    return@forEach // Skip, not ready for retry yet
                }

                // Process the message...
            } catch (e: Exception) {
                // Update attempt count and schedule for retry
            }
        }
    }

    private fun calculateBackoff(attemptCount: Int): Long {
        return minOf(1000L * (2.0.pow(attemptCount)).toLong(), 60_000L)
    }
}
```

### 3. Dead Letter Queue

For messages that fail after max retries, implement a dead letter queue:

```kotlin
class OrderEffectProcessor(...) {
    suspend fun processPending() {
        val pending = database.getPendingOutboxMessages(limit = 100)

        pending.forEach { message ->
            try {
                // ... process message
            } catch (e: Exception) {
                val newAttemptCount = message.attemptCount + 1
                if (newAttemptCount >= MAX_RETRIES) {
                    // Move to dead letter queue for manual investigation
                    database.moveToDeadLetterQueue(message)
                    database.updateMessageStatus(message.id, OutboxStatus.FAILED)
                } else {
                    // Schedule for retry
                    database.updateMessageForRetry(message.id, newAttemptCount, e.message)
                }
            }
        }
    }
}
```

### 4. Monitoring

Monitor your outbox for stuck messages:

```sql
-- Messages older than 5 minutes still pending
SELECT * FROM outbox
WHERE status = 'PENDING'
  AND created_at < UNIX_TIMESTAMP() * 1000 - 300000;
```

### 5. Serialization

Use a robust serialization format like JSON, Protocol Buffers, or Avro:

```kotlin
// Using kotlinx.serialization
@Serializable
data class EmailEffectData(
    val recipient: String,
    val subject: String,
    val body: String
)

class SendEmail(...) : DeferrableEffect<...> {
    override fun serialize(value: Order): Result<EffectPayload> = Result.success(
        EffectPayload(
            effectType = "send_email",
            data = Json.encodeToString(EmailEffectData(recipient, subject, body))
        )
    )
}
```

## When to Use the Outbox Pattern

Use the outbox pattern when:
- ✅ You need guaranteed delivery of side effects
- ✅ You're working with external systems (emails, webhooks, message queues)
- ✅ You want to decouple state changes from effect execution
- ✅ You need to handle failures gracefully

Don't use it when:
- ❌ Effects are simple and can be retried by the caller
- ❌ You don't have transactional storage
- ❌ Eventual consistency is not acceptable

## See Also

- [Implementation Guide](implementation_guide.md)
