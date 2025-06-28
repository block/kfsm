# kFSM: Type-Safe Finite State Machines for Kotlin

kFSM provides a robust, type-safe implementation of finite state machines in Kotlin, with optional Guice integration for dependency injection support.

## Core Concepts

kFSM is built around four fundamental concepts:

1. **States** - Represent the possible conditions of your entity
2. **Values** - The entities that move between states
3. **Transitions** - Rules and effects for moving between states
4. **Transitioners** - Orchestrate and validate state changes

## Modules

### Core Library (`lib`)

The core module provides the fundamental state machine implementation with these key features:

* Type-safe state transitions with compile-time verification
* Flexible state validation through invariants
* Path finding between states
* Mermaid diagram generation for documentation
* Both synchronous and asynchronous transition support
* Comprehensive testing utilities

Key types:
```kotlin
// Define states
sealed class MyState : State<ID, MyValue, MyState>

// Define values that transition between states
class MyValue : Value<ID, MyValue, MyState>

// Create transitions
val transition = Transition(FromState, ToState) { value ->
    // Validation and effects
    Result.success(value)
}

// Use the transitioner
val transitioner = Transitioner(transitions)
transitioner.transition(value, fromState, toState)
```

### Guice Integration (`lib-guice`)

The Guice module adds dependency injection support with these features:

* Automatic discovery of transitions
* Annotation-based configuration
* Integration with existing Guice modules
* Support for multiple state machines

Example usage:
```kotlin
class MyModule : KfsmModule() {
    override fun configure() {
        install(KfsmModule())
        bind<MyTransitions>().asEagerSingleton()
    }
}

@TransitionDefinition
class MyTransitions @Inject constructor(
    private val service: MyService
) {
    fun getTransitions(): Set<Transition<...>> = setOf(
        // Your transitions here
    )
}
```

## Key Features

### Type Safety

kFSM leverages Kotlin's type system to ensure:
* States are properly defined and sealed
* Transitions are between compatible states
* Values match their state machine's type parameters

### Validation

Multiple levels of validation ensure correctness:
* Compile-time type checking
* Runtime state machine verification
* Custom state invariants
* Transition-specific validation

### Documentation

Built-in documentation support:
* Mermaid diagram generation
* State reachability analysis
* Path finding between states

### Testing

Comprehensive testing support:
* State machine verification
* Transition testing utilities
* Path validation
* Invariant checking

## Best Practices

1. **State Definition**
   * Use sealed class hierarchies for states
   * Keep states immutable
   * Define clear invariants

2. **Transitions**
   * Make transitions single-purpose
   * Include proper validation
   * Handle errors gracefully

3. **Values**
   * Keep values immutable
   * Include only essential state
   * Use proper type parameters

4. **Integration**
   * Verify state machines at startup
   * Generate documentation
   * Use dependency injection when available

## Example

A simple traffic light implementation:

```kotlin
sealed class TrafficLightState : State<String, TrafficLight, TrafficLightState>
object Red : TrafficLightState()
object Yellow : TrafficLightState()
object Green : TrafficLightState()

class TrafficLight : Value<String, TrafficLight, TrafficLightState>

val redToGreen = Transition(Red, Green) { light ->
    Result.success(light)
}

val transitioner = Transitioner(setOf(redToGreen))
val light = TrafficLight()
transitioner.transition(light, Red, Green)
```

## Additional Resources

* See the test files for working examples
* Check the package documentation for detailed API information
* Review the Mermaid diagrams for visual state machine representations
