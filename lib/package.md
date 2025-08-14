# KFSM - Kotlin Finite State Machine

A lightweight, type-safe finite state machine implementation in Kotlin.

## Overview

KFSM provides a simple and intuitive way to define and manage state machines in Kotlin. It leverages the language's type system to ensure that state transitions are valid and that all possible states are reachable.

## Key Features

* **Type Safety**: Compile-time verification of state transitions
* **Sealed Class Integration**: Seamless integration with Kotlin's sealed classes
* **Transition Validation**: Automatic validation that all states are reachable
* **Documentation Generation**: Built-in Mermaid diagram generation
* **Guice Integration**: Optional Guice module for dependency injection

## Core Components

* [State] - Represents a state in the state machine
* [Value] - A value that can transition between states
* [Transition] - Defines how to move from one state to another
* [Transitioner] - Executes transitions and manages state changes
* [StateMachine] - The core state machine interface that manages transitions and state changes.
* [StateMachineUtils] - Provides utilities for verifying state machine completeness and generating documentation.

## Quick Start

1. Define your states as a sealed class extending [State]
2. Create your value class extending [Value]
3. Define transitions extending [Transition]
4. Verify your state machine using `StateMachineUtils.verify()`
5. Generate documentation using `StateMachineUtils.mermaid()`

## Example

```kotlin
sealed class TrafficLight : State<String, TrafficLight, TrafficLight> {
  object Red : TrafficLight() {
    override val subsequentStates = setOf(Green)
  }
  object Green : TrafficLight() {
    override val subsequentStates = setOf(Yellow)
  }
  object Yellow : TrafficLight() {
    override val subsequentStates = setOf(Red)
  }
}

data class TrafficValue(
  override val state: TrafficLight,
  override val id: String
) : Value<String, TrafficValue, TrafficLight>

class TrafficTransitioner : Transitioner<String, TrafficTransition, TrafficValue, TrafficLight> {
  override fun execute(value: TrafficValue, transition: TrafficTransition): Result<TrafficValue> {
    return when (transition) {
      is TrafficTransition.Change -> Result.success(value.copy(state = transition.targetState))
    }
  }
}

sealed class TrafficTransition : Transition<String, TrafficValue, TrafficLight> {
  data class Change(val targetState: TrafficLight) : TrafficTransition()
}

// Verify your state machine
StateMachineUtils.verify(TrafficLight.Red).getOrThrow()

// Generate documentation
val diagram = StateMachineUtils.mermaid(TrafficLight.Red).getOrThrow()
```

## Installation

Add the following to your `build.gradle.kts`:

```kotlin
dependencies {
  implementation("app.cash:kfsm:1.0.0")
}
```

For Guice integration:

```kotlin
dependencies {
  implementation("app.cash:kfsm-guice:1.0.0")
}
```
