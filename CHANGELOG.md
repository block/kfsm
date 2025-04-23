# Change Log

## [Unreleased]

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

