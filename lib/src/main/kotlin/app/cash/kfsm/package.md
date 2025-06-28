# Package app.cash.kfsm

The core kFSM package provides a robust, type-safe implementation of finite state machines in Kotlin. It is designed to help you model complex state transitions while maintaining compile-time safety and verification.

## Key Components

### State Management

* [State] - Base class for defining states in your state machine. States know their subsequent states and can validate invariants.
* [States] - Utility object for working with collections of states.
* [Value] - Interface for entities that can transition between states.

### Transitions

* [Transition] - Defines how entities move between states, including validation and effects.
* [Transitioner] - Manages state transitions and ensures they follow defined rules.
* [TransitionerAsync] - Coroutine-based version of Transitioner for asynchronous operations.

### Validation & Safety

* [Invariant] - Defines conditions that must hold true for a state.
* [InvariantDsl] - DSL for creating state invariants.
* [InvalidStateTransition] - Exception thrown when an invalid transition is attempted.
* [NoPathToTargetState] - Exception thrown when no valid path exists to reach a target state.

### Utilities

* [StateMachine] - Provides utilities for verifying state machine completeness and generating documentation.

## Example Usage

```kotlin
// Define your states
sealed class TrafficLightState : State<String, TrafficLight, TrafficLightState>
object Red : TrafficLightState()
object Yellow : TrafficLightState()
object Green : TrafficLightState()

// Define your value type
class TrafficLight : Value<String, TrafficLight, TrafficLightState>

// Create transitions
val redToGreen = Transition(Red, Green) { light ->
    // Validation and effects here
    Result.success(light)
}

// Use the transitioner
val transitioner = Transitioner(setOf(redToGreen))
val light = TrafficLight()
transitioner.transition(light, Red, Green)
```

## Best Practices

1. Make your states a sealed class hierarchy
2. Use value objects that are immutable
3. Define clear invariants for each state
4. Verify your state machine using `StateMachine.verify()`
5. Generate documentation using `StateMachine.mermaid()`
