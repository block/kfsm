# kFSM

Finite State Machinery for Kotlin

## Overview

kFSM provides a type-safe, compile-time verified finite state machine implementation for Kotlin. It consists of two main modules:

### Core Library (`lib`)

The core kFSM library providing type-safe, compile-time verified finite state machine functionality. It consists of four main components:

1. **State** - Represents the different states in your state machine
2. **Value** - The entity that transitions between states
3. **Transition** - Defines the effects and rules for state changes
4. **Transitioner** - Manages the transition process and persistence

### Guice Integration (`lib-guice`)

Guice integration for kFSM, enabling automatic discovery and dependency injection of transitions and transitioners. This module simplifies the setup and management of state machines in Guice-based applications.

## Key Features

- Type-safe state transitions
- Compile-time verification of state machine validity
- Support for state invariants
- Coroutine support with `TransitionerAsync`
- Comprehensive testing utilities
- Mermaid diagram generation for documentation
- Automatic discovery and dependency injection (Guice module)

## Usage

See the main README.md for detailed usage examples and the test files for working examples. 