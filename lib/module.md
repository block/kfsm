# Module lib

The core KFSM library provides the fundamental building blocks for creating type-safe finite state machines in Kotlin.

## Key Components

### State
The base class for defining states in your state machine. Each state knows:
- Which states it can transition to directly
- What invariants must hold while in this state
- How to find paths to other reachable states

### Value
An interface for objects that can transition between states. Values:
- Track their current state
- Can be updated to new states through transitions
- Maintain their identity across state changes

### Transition
Defines valid movements between states, including:
- Source states that can use this transition
- Target state after the transition
- Optional effects that occur during the transition

### Transitioner
Handles the mechanics of moving values between states:
- Validates state changes
- Manages pre/post transition hooks
- Handles persistence if needed

### StateMachine
Provides utilities for working with state machines:
- Verification of state machine properties
- Documentation generation
- Path finding between states

## Getting Started

See the [README](../README.md) for setup instructions and basic usage examples.
