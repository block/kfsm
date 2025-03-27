# KFSM Guice Integration Design

## Overview
This document outlines the design for adding Guice integration support to the KFSM library (https://github.com/block/kfsm). The goal is to provide automatic transition discovery and dependency injection support while maintaining KFSM's core principles of type safety and simplicity.

## Core Components

### 1. Base Annotation
```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class TransitionDefinition
```

### 2. Generic Base Module
```kotlin
abstract class KfsmModule<T, S : State<S>>(
    private val basePackage: String
) : AbstractModule() {
    override fun configure() {
        val transitionBinder = Multibinder.newSetBinder(
            binder(),
            typeOf<Transition<T, S>>()
        )
        
        val reflections = Reflections(
            ConfigurationBuilder()
                .forPackages(basePackage)
                .setScanners(Scanners.TypesAnnotated)
        )
        
        reflections
            .getTypesAnnotatedWith(TransitionDefinition::class.java)
            .filterIsInstance<Class<out Transition<T, S>>>()
            .forEach { transitionClass ->
                transitionBinder.addBinding().to(transitionClass)
            }
    }
}
```

### 3. Generic State Machine Wrapper
```kotlin
@Singleton
class StateMachine<T, S : State<S>> @Inject constructor(
    private val transitions: Set<Transition<T, S>>,
    private val transitioner: Transitioner<Transition<T, S>, T, S>
) {
    private val transitionsByType: Map<Class<out Transition<T, S>>, Transition<T, S>> =
        transitions.associateBy { it::class.java }
    
    fun getAvailableTransitions(state: S): Set<Transition<T, S>> =
        transitions.filter { it.isValidFor(state) }.toSet()
    
    fun <TR : Transition<T, S>> getTransition(type: Class<TR>): TR =
        transitionsByType[type] as? TR 
            ?: throw IllegalArgumentException("No transition found for type ${type.simpleName}")
    
    fun execute(entity: T, transition: Transition<T, S>): Result<T> =
        transitioner.transition(entity, transition)
}
```

## Usage Example
```kotlin
// Define a transition
@TransitionDefinition
class MyTransition @Inject constructor(deps: MyDeps) : Transition<MyEntity, MyState>(/*...*/)

// Create a module
class MyStateMachineModule : KfsmModule<MyEntity, MyState>("com.example.myapp")

// Use the state machine
@Inject
lateinit var stateMachine: StateMachine<MyEntity, MyState>
```

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