# kFSM: Type-Safe Finite State Machines for Kotlin

kFSM provides a robust, type-safe implementation of finite state machines in Kotlin, designed for reliable distributed workflows with transactional outbox support.

## Core Concepts

kFSM is built around these fundamental concepts:

1. **States** - Represent the possible conditions of your entity
2. **Values** - The entities that move between states
3. **Transitions** - Pure `decide()` functions that determine state changes and effects
4. **Effects** - Side effects stored in a transactional outbox for reliable execution
5. **StateMachine** - Validates and applies state transitions atomically
6. **EffectProcessor** - Executes effects from the outbox and chains workflows

## Architecture

```
Transition.decide() → StateMachine.transition() → Repository.saveWithOutbox()
        │                       │                         │
        │                       │                         ▼
   Pure function          Validates &            Atomic save of state
   (no side effects)      applies decision       + outbox messages
                                                         │
                                                         ▼
                          EffectProcessor.processAll() ──┘
                                   │
                                   ▼
                          EffectHandler.handle() → May trigger more transitions
```

## Modules

### Core Library (`lib`)

The core module provides the fundamental state machine implementation:

* **Pure decision logic** - Transitions return `Decision` objects, not side effects
* **Transactional outbox** - Effects stored atomically with state changes
* **Type-safe transitions** with compile-time verification
* **Flexible validation** through invariants
* **AwaitableStateMachine** for synchronous-style APIs over async workflows

Key types:
```kotlin
// Define states with allowed transitions
sealed class OrderState : State<OrderState>() {
    data object Pending : OrderState() {
        override fun transitions() = setOf(Confirmed, Cancelled)
    }
    data object Confirmed : OrderState() { ... }
}

// Define effects as a sealed class
sealed class OrderEffect : Effect {
    data class SendEmail(val orderId: String, val email: String) : OrderEffect()
    data class ChargePayment(val orderId: String, val amount: Long) : OrderEffect()
}

// Define transitions with pure decide() functions
class ConfirmOrder(private val paymentId: String) : Transition<String, Order, OrderState, OrderEffect>(
    from = OrderState.Pending,
    to = OrderState.Confirmed
) {
    override fun decide(value: Order): Decision<OrderState, OrderEffect> =
        Decision.accept(
            state = OrderState.Confirmed,
            effects = listOf(
                OrderEffect.SendEmail(value.id, value.email),
                OrderEffect.ChargePayment(value.id, value.total)
            )
        )
}

// Use the state machine
val stateMachine = StateMachine(repository)
stateMachine.transition(order, ConfirmOrder(paymentId))
```

### jOOQ Integration (`lib-jooq`)

The jOOQ module provides production-ready outbox utilities:

* **JooqOutbox** - `Outbox` implementation using `SELECT ... FOR UPDATE SKIP LOCKED`
* **PollingEffectProcessor** - Background processor with exponential backoff
* **JacksonOutboxSerializer** - Jackson-based serialization for sealed class effects
* **OutboxSchema** - DDL for MySQL and PostgreSQL

Example usage:
```kotlin
// Create the outbox with a serializer
val serializer = JacksonOutboxSerializer.forSealedClassWithStringId(OrderEffect::class)
val outbox = JooqOutbox(dsl, serializer)

// Start background processing
val processor = PollingEffectProcessor(
    outbox = outbox,
    handler = orderEffectHandler,
    stateMachine = orderStateMachine,
    valueLoader = { id -> repository.findById(id) },
    config = PollingConfig(
        baseInterval = Duration.ofMillis(100),
        maxInterval = Duration.ofSeconds(5)
    )
)
processor.start()
```

## Key Features

### Pure Decision Logic

Transitions use pure `decide()` functions that return a `Decision`:
* Easy to test without mocking infrastructure
* Effects are descriptive data, not imperative calls
* Clear separation between deciding and doing

### Transactional Outbox Pattern

Effects are stored atomically with state changes:
* At-least-once delivery semantics
* Survives crashes and deployments
* Enables reliable multi-step workflows

### Concurrent Processing

The jOOQ module uses `SKIP LOCKED` for efficient parallel processing:
* Multiple instances can process different entities concurrently
* Per-entity ordering is preserved
* No lock contention or blocking

### Type Safety

kFSM leverages Kotlin's type system to ensure:
* States are properly defined with allowed transitions
* Transitions match compatible from/to states
* Effects are type-safe sealed classes

## Testing

Pure decision logic makes testing straightforward:

```kotlin
@Test
fun `confirm order produces email and payment effects`() {
    val order = Order(id = "123", state = Pending, email = "test@example.com")
    val transition = ConfirmOrder(paymentId = "pay-456")
    
    val decision = transition.decide(order)
    
    decision.shouldBeInstanceOf<Decision.Accept<*, *>>()
    decision.state shouldBe Confirmed
    decision.effects shouldContain OrderEffect.SendEmail("123", "test@example.com")
}
```

## Example

See the `example` module for a complete document upload workflow demonstrating:

* MySQL-backed persistence with jOOQ
* Transactional outbox using `lib-jooq`
* Async virus scanning with effect chaining
* AwaitableStateMachine for sync-style API

```kotlin
// The workflow: Created → Uploading → AwaitingScan → Scanning → Accepted/Quarantined
val result = stateMachine.transition(document, StartUpload(fileContent))
effectProcessor.processAll()  // Executes upload, triggers scan, handles results
```

## Additional Resources

* See the `example` module for working integration tests
* Check the package documentation for detailed API information
* Review the `docs/V2.md` file for migration guidance and background processing options
