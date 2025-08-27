# Change Log

## [Unreleased]

### Added
* Added `StateMachine::advance` method to automatically progress to the next state using a state selector, 
  enabling dynamic state transitions without explicitly specifying the target state.
* Exposed the state values $to and $from in the inline effect syntax. e.g. 
  ```kotlin
  A becomes { 
    B via { it.copy(message = "from $from to $to" ) } 
  }
  ```

## [0.11.0]

### Added
* Introduced MachineBuilder DSL for creating type-safe state machines with a more intuitive syntax

### Breaking
* In order to make room for a new StateMachine type, the existing object StateMachine, which was a short collection of
  static utilities, has been renamed to StateMachineUtilities.

## [0.10.3]

Modified maven publishing configuration again. It now works with the new maven central portal.

## [0.10.2]

Modified maven publishing configuration.

## [0.10.1]

### Added
* Adds `shortestPathTo` method on `State`

## [0.10.0]

### Breaking
* Added `from` parameter to `persist` method in `Transitioner` and `TransitionerAsync` to provide the previous state during persistence operations. This allows for more context-aware persistence operations that can take into account both the previous and new state.

### Added
* Added support for state invariants, allowing you to define conditions that must hold true for values in specific states. This includes:
  * A new `invariant` DSL function for defining state-specific conditions
  * Automatic validation of invariants during state transitions
  * Support for custom error messages when invariants are violated
  * Integration with the existing state machine validation system

## [0.9.0]

### Breaking
* Renamed `InvalidStateTransition` to `InvalidStateForTransition` to better reflect its purpose. This is a breaking change that requires updating any code that catches or references this exception type.

### Added
* Added `transitionToState` function to `StateMachine` that allows transitioning to a specific state if it's immediately reachable. This provides a simpler API for cases where the caller knows the target state and doesn't need to specify a particular transition.

## [0.8.3]

* Fixed dependency configuration in order to fix a runtime failure with lib-guice.

## [0.8.2]

* Fixed gradle properties for lib-guice to enable publishing to the remote repository.

## [0.8.0]

### Breaking

* Added ID type parameter to Value interface to support custom identifier types. This is a breaking change that requires:
  * Adding an ID type parameter to all Value implementations
  * Adding an ID type parameter to all Transition, Transitioner, and related classes
  * Implementing the `id` property in all Value implementations
  * Updating all type references to include the ID type parameter

## [0.7.5]

* Added id(): String function to Value.

## [0.7.4]

* Exposes state on `InvalidStateTransition`
* Added additional information to invalid transition error messages.

## [0.7.0]

### Breaking

* Added type argument to State to allow for the ability to add customer behaviour to your states. This is a breaking
  change as it will require you to update your state classes to include the type argument.

## [0.6.0]

### Breaking

* Converted the default usages of the library to be non-suspending. Added the suspending variants back as `*Async`.
* Added the transition to the persist function and moved persist to be an open method instead of function injection
  constructor argument.

## [0.5.1]

### Breaking

* As promised with the 0.5 release, the Arrow specific library has been removed. Please migrate to `lib`.
  * Replace `NonEmptySet` with `States`.
  * Replace ErrorOr/Either with `Result`.
* Removed the v0.3 API. Please migrate to the new API.

## [0.5.0]

### Breaking

* Introduced States as a proxy for NonEmptySet when defining Transitions. This allows for safer transition definitions
  in the non-Arrow library.
* The Arrow specific library will eventually be removed, as the non-Arrow presenting API has equivalent semantics.


## [0.4.0]

### Breaking

* Upon request, introduced a new API that uses kotlin native types and does not include Arrow as a dependency.
  The original lib is renamed `lib-arrow`.

## [0.3.0]

### Breaking

* Refined type aliases on Transitioner so that implementations are free to define a base transition type that may
  implement common functionality. For example, a new base transition type can define a common way to execute
  side-effects that occur in pre and post hook transitioner functions. See TransitionerTest use of 
  `specificToThisTransitionType` for an example.

## [0.2.0]

### Breaking

* Changes to new API's method signatures and types required to integrate with its first real project. 

## [0.1.0]

### Breaking

* `StateMachine.verify` no longer requires a second argument to declare the base type of the state machine. This is now
  inferred from the first argument.`

### Added

* `StateMachine.mermaid` is a new utility that will generate mermaid diagram from your state machine.
* New simplified API is being introduced to make it easier to use the library. This can be found in the package
  `app.cash.kfsm`. It is not yet ready for production use, but we are looking for feedback on the new API.


## [0.0.2] - 2023-09-11

### Added

* Initial release from internal
