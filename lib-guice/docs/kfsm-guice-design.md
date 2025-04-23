# KFSM Guice Integration Design

## Overview

The KFSM Guice integration module provides automatic discovery and dependency injection for KFSM components using Google Guice. It enables developers to define state machine transitions and transitioners using annotations, which are then automatically discovered and bound by the framework.

## Core Components

### 1. Annotations

#### `@TransitionDefinition`
- Marks a class as a transition that should be automatically discovered and bound
- Applied to classes that extend `Transition<V, S>`
- Used by `KfsmModule` to discover and bind transitions

#### `@TransitionerDefinition`
- Marks a class as a transitioner that should be automatically discovered and bound
- Applied to classes that extend `Transitioner<Transition<V, S>, V, S>`
- Used by `KfsmModule` to discover and bind transitioners

### 2. KfsmModule

The core module that handles automatic discovery and binding of state machine components:

```kotlin
abstract class KfsmModule<V : Value<V, S>, S : State<S>>(
    private val basePackage: String? = null,
    private val types: KfsmMachineTypes<V, S>,
) : AbstractModule()
```

Key features:
- Automatically discovers and binds transitions annotated with `@TransitionDefinition`
- Automatically discovers and binds transitioners annotated with `@TransitionerDefinition`
- Uses package scanning to find components
- Provides type-safe binding through `KfsmMachineTypes`

### 3. StateMachine

A Guice-managed wrapper around KFSM's `Transitioner` that provides convenient access to discovered transitions:

```kotlin
@Singleton
class StateMachine<V : Value<V, S>, S : State<S>> @Inject constructor(
    private val transitions: Set<Transition<V, S>>,
    private val transitioner: Transitioner<Transition<V, S>, V, S>
)
```

Features:
- Manages the set of available transitions
- Provides type-safe access to specific transitions
- Executes transitions using the underlying transitioner

## Usage Flow

1. **Define States and Values**
    - Create state and value types that implement `State` and `Value` interfaces
    - Example: `OrderState` enum and `Order` data class

2. **Create Transitions**
    - Define transition classes extending `Transition<V, S>`
    - Annotate with `@TransitionDefinition`
    - Use dependency injection for transition dependencies
    - Example: `ProcessOrder` transition

3. **Create Transitioner (Optional)**
    - Define transitioner class extending `Transitioner<Transition<V, S>, V, S>`
    - Annotate with `@TransitionerDefinition`
    - Example: `OrderTransitioner`

4. **Create Guice Module**
    - Extend `KfsmModule` with your types
    - Optionally specify base package for scanning
    - Example: `OrderModule`

5. **Use State Machine**
    - Inject `StateMachine` into your services
    - Use type-safe methods to access transitions
    - Execute transitions with proper error handling

## Implementation Details

### Package Scanning

The module uses the Reflections library to scan for annotated classes:

```kotlin
val reflections = Reflections(
    ConfigurationBuilder()
        .forPackages(packageToScan)
        .setScanners(Scanners.TypesAnnotated)
)
```

### Type Safety

Type safety is ensured through:
- Generic type parameters in `KfsmModule`
- `KfsmMachineTypes` for type-safe binding
- Type-safe transition access in `StateMachine`

### Dependency Injection

Components are bound using Guice's multibinder:

```kotlin
val transitionBinder = Multibinder.newSetBinder(binder(), types.transition)
```

## Testing

The module is designed to be easily testable:
- Components can be tested in isolation
- Transitions can be tested independently
- Integration tests can verify the full state machine flow
- Kotest provides excellent support for testing state machines

## Future Enhancements

### 1. Transition Groups
```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class TransitionGroup(val name: String)

@TransitionDefinition
@TransitionGroup("withdrawal")
class MyTransition : Transition<T, S>
```

### 2. Transition Priority
```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class TransitionPriority(val priority: Int)

@TransitionDefinition
@TransitionPriority(1)
class HighPriorityTransition : Transition<T, S>
```

### 3. Conditional Transitions
```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalTransition(val condition: String)

@TransitionDefinition
@ConditionalTransition("feature.enabled")
class FeatureGatedTransition : Transition<T, S>
```

### 4. Transition Metadata
```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class TransitionMetadata(
    val description: String = "",
    val tags: Array<String> = [],
    val version: String = "1.0"
)
```

### 5. State Machine Visualization
Add support for generating state machine diagrams in formats like PlantUML or Mermaid.

### 6. Testing Support
```kotlin
class KfsmTestModule<T, S : State<S>> : AbstractModule() {
    private val mockTransitions = mutableSetOf<Transition<T, S>>()
    
    fun addMockTransition(transition: Transition<T, S>) {
        mockTransitions.add(transition)
    }
    
    override fun configure() {
        val binder = Multibinder.newSetBinder(
            binder(), 
            typeOf<Transition<T, S>>()
        )
        mockTransitions.forEach { transition ->
            binder.addBinding().toInstance(transition)
        }
    }
}
```

## Implementation Strategy

1. Start with core components:
    - TransitionDefinition annotation
    - KfsmModule base class
    - StateMachine wrapper

2. Add basic testing support

3. Add enhancement features in order of value:
    - Transition Groups (helps organization)
    - Transition Priority (helps control flow)
    - Visualization (helps documentation)
    - Metadata (helps maintenance)
    - Conditional Transitions (helps feature control)

## Key Principles

1. Maintain KFSM's type safety
2. Keep core functionality simple
3. Make enhancements opt-in
4. Preserve existing KFSM API compatibility
5. Focus on compile-time safety where possible

## Dependencies Required

- Guice
- Reflections library
- Kotlin reflection

## Notes for Implementation

- Consider making the StateMachine wrapper interface-based for better testing
- Add proper error handling for reflection failures
- Consider performance implications of reflection at startup
- Add comprehensive documentation and examples
- Include migration guide for existing KFSM users
- Add proper nullability annotations
- Consider multi-module support for transition discovery
- Add proper logging for transition discovery and binding

This design allows for gradual implementation while maintaining KFSM's core values of type safety and simplicity.
