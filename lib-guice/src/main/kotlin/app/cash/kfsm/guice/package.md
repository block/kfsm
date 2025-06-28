# Package app.cash.kfsm.guice

The kFSM Guice integration package provides seamless dependency injection support for kFSM state machines. It enables automatic discovery and injection of transitions and transitioners, making it easier to integrate state machines into Guice-based applications.

## Key Components

### Module Configuration

* [KfsmModule] - Guice module that sets up automatic discovery and binding of state machine components.
* [StateMachine] - Extended functionality for Guice-managed state machines.

### Annotations

* [TransitionDefinition] - Marks a class as providing state transitions.
* [TransitionerDefinition] - Configures how a transitioner should be constructed.

## Features

* Automatic discovery of transitions
* Dependency injection for transition implementations
* Support for multiple state machines in one application
* Integration with existing Guice modules

## Example Usage

```kotlin
// Define your module
class MyAppModule : KfsmModule() {
    override fun configure() {
        // Basic setup
        install(KfsmModule())
        
        // Bind your transitions
        bind<MyTransitions>().asEagerSingleton()
    }
}

// Mark your transitions
@TransitionDefinition
class MyTransitions @Inject constructor(
    private val dependency: SomeService
) {
    fun getTransitions(): Set<Transition<...>> {
        // Define your transitions
    }
}

// Use in your application
@Inject
lateinit var transitioner: Transitioner<...>
```

## Best Practices

1. Use `@TransitionDefinition` to mark classes containing transitions
2. Consider using `@TransitionerDefinition` for custom transitioner configuration
3. Inject dependencies into your transition classes
4. Use eager singletons for transition definitions
5. Configure the KfsmModule early in your application setup
