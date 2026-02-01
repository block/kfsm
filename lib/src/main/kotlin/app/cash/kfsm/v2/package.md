# Package app.cash.kfsm.v2

The core kFSM package provides a robust, type-safe implementation of finite state machines in Kotlin with a transactional outbox pattern for reliable effect processing.

## Key Components

### State & Values

* [State] - Base class for defining states. States declare their allowed transitions and optional invariants.
* [Value] - Interface for entities that transition between states. Values are immutable and carry domain data.

### Transitions & Decisions

* [Transition] - Defines valid state changes with a pure `decide` function that determines outcomes.
* [Decision] - Result of a transition: either `Accept` (with new state and effects) or `Reject`.

### State Machine

* [StateMachine] - Orchestrates transitions, validates invariants, and persists state changes with outbox messages atomically.
* [Repository] - Interface for persisting values and outbox messages in the same transaction.

### Effects & Processing

* [Effect] - Marker interface for side effects to be executed after state transitions.
* [EffectHandler] - Executes effects and returns outcomes (transitions or completion).
* [EffectOutcome] - Result of effect execution: `TransitionProduced`, `Completed`, or `FailedWithTransition`.
* [EffectProcessor] - Reads pending outbox messages and executes effects, creating a feedback loop for multi-step workflows.
* [Outbox] - Storage interface for outbox messages.
* [OutboxMessage] - A message in the transactional outbox with status tracking and retry support.

### Async & Awaitable

* [AwaitableStateMachine] - Wrapper that provides suspending transitions with timeout, using database polling for multi-instance deployments.
* [PendingRequestStore] - Storage interface for pending requests that supports distributed coordination.
* [PendingRequestStatus] - Status of a pending request (Waiting, Completed, Failed, NotFound).

### Invariants

* [Invariant] - Defines conditions that must hold true for a value in a particular state.
* [invariant] - Factory function for creating invariants from predicates.
* [InvariantViolation] - Exception thrown when an invariant fails.

### Utilities

* [StateMachineUtilities] - Provides `verify()` for state machine completeness checking and `mermaid()` for diagram generation.

## Example Usage

```kotlin
// Define your states
sealed class OrderState : State<OrderState>() {
  data object Pending : OrderState() {
    override fun transitions() = setOf(Confirmed, Cancelled)
  }
  data object Confirmed : OrderState() {
    override fun transitions() = setOf(Shipped)
  }
  data object Shipped : OrderState() {
    override fun transitions() = setOf(Delivered)
  }
  data object Delivered : OrderState() {
    override fun transitions() = emptySet()
  }
  data object Cancelled : OrderState() {
    override fun transitions() = emptySet()
  }
}

// Define your value type
data class Order(
  override val id: String,
  override val state: OrderState,
  val customerId: String
) : Value<String, Order, OrderState> {
  override fun update(newState: OrderState) = copy(state = newState)
}

// Define effects
sealed class OrderEffect : Effect {
  data class SendConfirmationEmail(val orderId: String) : OrderEffect()
}

// Define transitions
class ConfirmOrder : Transition<String, Order, OrderState, OrderEffect>(
  from = OrderState.Pending,
  to = OrderState.Confirmed
) {
  override fun decide(value: Order) = Decision.accept(
    state = OrderState.Confirmed,
    effects = listOf(OrderEffect.SendConfirmationEmail(value.id))
  )
}

// Use the state machine
val stateMachine = StateMachine(orderRepository)
val result = stateMachine.transition(order, ConfirmOrder())
```

## Best Practices

1. Make your states a sealed class hierarchy
2. Use immutable value objects
3. Define clear invariants for each state
4. Verify your state machine using `StateMachineUtilities.verify()`
5. Generate documentation using `StateMachineUtilities.mermaid()`
6. Make effects idempotent (they may be retried)
