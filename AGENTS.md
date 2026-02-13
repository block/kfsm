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
// Expression bodies
fun <S : State<S>> verify(head: S): Result<Set<State<S>>> =
  verify(head, baseType(head))

// Lazy properties for computed values
val subsequentStates: Set<S> by lazy { transitions() }

// Derived boolean properties
val isTerminal: Boolean get() = subsequentStates.isEmpty()

// Functional collection transforms
val transitions = stateSet.flatMap { from ->
  from.subsequentStates.map { to -> "${from::class.simpleName} --> ${to::class.simpleName}" }
}.sorted()

// Sealed class hierarchies for type-safe ADTs
sealed class Decision<out V, S : State<S>, E : Effect> {
  data class Accept<V, S : State<S>, E : Effect>(val value: V, ...) : Decision<V, S, E>()
  data class Reject<S : State<S>, E : Effect>(val reason: String) : Decision<Nothing, S, E>()
}

// when expressions for exhaustive matching
when (val decision = transition.decide(value)) {
  is Decision.Accept -> { ... }
  is Decision.Reject -> Result.failure(RejectedTransition(decision.reason))
}
```

### ❌ Don't

```kotlin
// No imperative loops when functional transforms work
val results = mutableListOf<String>()
for (state in stateSet) {
  for (to in state.subsequentStates) {
    results.add("${state::class.simpleName} --> ${to::class.simpleName}")
  }
}

// No var when val works
var isTerminal = false
if (subsequentStates.isEmpty()) isTerminal = true
```

## Testing

Kotest StringSpec with `IsolationMode.InstancePerTest`. Tests live in `lib/src/test/kotlin/app/cash/kfsm/v2/`.

The `exemplar/` package contains a complete reference state machine (`DocumentUpload`) used by tests — update it when adding new library features.

The `testing/` package contains in-memory test doubles (`InMemoryOutbox`, `InMemoryPendingRequestStore`).

### ✅ Do

```kotlin
class SomeFeatureTest : StringSpec({
  isolationMode = IsolationMode.InstancePerTest

  // In-memory repository — no mocking
  val repository = object : Repository<String, DocumentUpload, DocumentState, DocumentEffect> {
    override fun saveWithOutbox(
      value: DocumentUpload,
      outboxMessages: List<OutboxMessage<String, DocumentEffect>>
    ): Result<DocumentUpload> = Result.success(value)
  }

  "descriptive test name in lowercase" {
    val doc = DocumentUpload(id = "doc-1", state = DocumentState.Idle)
    val result = StateMachine(repository).transition(doc, RequestUpload("test.pdf", byteArrayOf()))

    result.shouldBeSuccess()
    result.getOrThrow().state shouldBe DocumentState.Uploading
  }
})
```

### ❌ Don't

```kotlin
// No mocking
val mockRepo = mockk<Repository<...>>()

// No JUnit assertions
assertEquals(expected, actual)
assertTrue(result.isSuccess)

// No AAA comments
// Arrange ... // Act ... // Assert ...
```

## Public API

The library uses the [Kotlin Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator). Changing public API signatures will fail the build — run `bin/gradle apiDump` to update the API dump after intentional changes.
