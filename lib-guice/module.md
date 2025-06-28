# Module lib-guice

This module provides integration between KFSM and Google Guice for dependency injection.

## Key Components

### KfsmModule
A Guice module that automatically discovers and binds:
- Transitions marked with @TransitionDefinition
- Transitioners marked with @TransitionerDefinition
- The StateMachine implementation

### StateMachine
A Guice-aware implementation that:
- Manages transitions between states
- Provides utilities for finding available transitions
- Handles execution of transitions

### Annotations
- @TransitionDefinition - Marks transitions for auto-discovery
- @TransitionerDefinition - Marks transitioners for auto-discovery

## Usage

1. Create your states, values, and transitions as normal
2. Annotate your transitions with @TransitionDefinition
3. Annotate your transitioner with @TransitionerDefinition
4. Install KfsmModule in your Guice injector
5. Inject the StateMachine where needed

See the test package for complete examples.
