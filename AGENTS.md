# kfsm

Kotlin Finite State Machine library. Open source at [block/kfsm](https://github.com/block/kfsm).

## Build

```bash
bin/gradle build                    # build + test
bin/gradle :lib:test                # lib tests only
bin/gradle :lib-jooq:test           # jooq tests only
```

## Structure

```
lib/          # Core library (State, Transition, Decision, Effects, StateMachine)
lib-jooq/     # jOOQ persistence integration
lib-guice/    # Guice DI integration
example/      # Example usage
```

Source lives under `app.cash.kfsm.v2` — the `v2` package is the active API.

## Kotlin Style

2-space indent. 120 char lines. No wildcard imports. Expression syntax over imperative.

### ✅ Do

```kotlin
// Sealed class hierarchies for states
sealed class OrderState : State<OrderState>() {
  data object Pending : OrderState() {
    override fun transitions() = setOf(Confirmed, Cancelled)
  }
  data object Confirmed : OrderState() {
    override fun transitions() = setOf(Shipped)
  }
}

// data object for singleton states, data class when state carries data
data class Rejected(val reason: String) : DocumentState() {
  override fun transitions() = emptySet()
}

// Override canTransitionTo for data class states (can't appear in transitions())
data object Uploading : DocumentState() {
  override fun transitions(): Set<DocumentState> = setOf(Scanning)
  override fun canTransitionTo(other: DocumentState) =
    super.canTransitionTo(other) || other is Rejected
}

// Expression-body decide functions
override fun decide(value: Order): Decision<Order, OrderState, OrderEffect> =
  Decision.accept(
    value = value.update(OrderState.Confirmed),
    effects = listOf(OrderEffect.SendConfirmation(value.id))
  )

// Immutable values with update()
data class Order(
  override val id: String,
  override val state: OrderState,
  val total: BigDecimal
) : Value<String, Order, OrderState> {
  override fun update(newState: OrderState): Order = copy(state = newState)
}
```

### ❌ Don't

```kotlin
// Don't mutate state directly — always use transitions via StateMachine
order.copy(state = OrderState.Confirmed)  // wrong — bypasses validation

// Don't put side effects in decide() — it must be pure
override fun decide(value: Order): Decision<Order, OrderState, OrderEffect> {
  database.save(value)  // wrong — use Effects for side effects
  return Decision.accept(value.update(OrderState.Confirmed))
}
```

## Testing

Kotest StringSpec with `IsolationMode.InstancePerTest`. In-memory `Repository` implementations for unit tests.

### ✅ Do

```kotlin
class OrderTest : StringSpec({
  isolationMode = IsolationMode.InstancePerTest

  val repository = object : Repository<String, Order, OrderState, OrderEffect> {
    override fun saveWithOutbox(value: Order, outboxMessages: List<OutboxMessage<String, OrderEffect>>): Result<Order> {
      return Result.success(value)
    }
  }
  val stateMachine = StateMachine(repository)

  "confirms a pending order" {
    val order = Order(id = "order-1", state = OrderState.Pending, total = 100.toBigDecimal())
    val result = stateMachine.transition(order, ConfirmOrder("pay-123"))

    result.shouldBeSuccess()
    result.getOrThrow().state shouldBe OrderState.Confirmed
  }

  "rejects transition from wrong state" {
    val order = Order(id = "order-1", state = OrderState.Pending, total = 100.toBigDecimal())
    val result = stateMachine.transition(order, ShipOrder("tracking-1"))

    result.shouldBeFailure()
  }
})
```

### ❌ Don't

```kotlin
// No mocking — use in-memory Repository implementations
val mockRepo = mockk<Repository<...>>()

// No JUnit assertions
assertEquals(OrderState.Confirmed, result.state)
assertTrue(result.isSuccess)
```

## Key Concepts

- **State** — sealed class extending `State<S>`, defines `transitions()` graph
- **Value** — domain object implementing `Value<ID, V, S>`, carries state
- **Transition** — declares `from`/`to` states, pure `decide()` function returns `Decision`
- **Decision** — `Accept` (new value + effects) or `Reject` (reason string)
- **Effects** — side effects stored in transactional outbox, `Effects.parallel()` or `Effects.ordered()`
- **StateMachine** — orchestrates: validate transition → decide → check invariants → persist atomically
