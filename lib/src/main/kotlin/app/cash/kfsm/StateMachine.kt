package app.cash.kfsm

import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.superclasses

/**
 * Provides utilities for working with state machines as a whole, including verification
 * and documentation generation.
 *
 * This object offers two main capabilities:
 * 1. Verifying that a state machine is complete (covers all possible states)
 * 2. Generating Mermaid markdown diagrams for documentation
 *
 * Example usage:
 * ```kotlin
 * // Verify your state machine
 * StateMachine.verify(initialState).getOrThrow()
 *
 * // Generate a Mermaid diagram
 * val diagram = StateMachine.mermaid(initialState).getOrThrow()
 * ```
 */
object StateMachine {

  /**
   * Verifies that a state machine covers all possible states.
   *
   * This method checks that all subtypes of the state's sealed class are reachable
   * from the given head state. It helps ensure that your state machine is complete
   * and that no states are accidentally unreachable.
   *
   * @param head The initial state to start verification from
   * @return A Result containing the set of all reachable states if verification succeeds
   * @throws InvalidStateMachine if any states are unreachable
   */
  fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> verify(head: S): Result<Set<State<ID, V, S>>> = verify(head, baseType(head))

  /**
   * Generates a Mermaid markdown diagram representing the state machine.
   *
   * The diagram shows all states and their possible transitions, making it easy to
   * visualize and document your state machine's structure.
   *
   * Example output:
   * ```mermaid
   * stateDiagram-v2
   *     [*] --> InitialState
   *     InitialState --> NextState
   *     NextState --> FinalState
   * ```
   *
   * @param head The initial state to start diagram generation from
   * @return A Result containing the Mermaid markdown string
   */
  fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> mermaid(head: S): Result<String> = walkTree(head).map { states ->
    listOf("stateDiagram-v2", "[*] --> ${head::class.simpleName}").plus(
      states.toSet().flatMap { from ->
        from.subsequentStates.map { to -> "${from::class.simpleName} --> ${to::class.simpleName}" }
      }.toList().sorted()
    ).joinToString("\n    ")
  }

  private fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> verify(head: S, type: KClass<out S>): Result<Set<State<ID, V, S>>> =
    walkTree(head).mapCatching { seen ->
      val notSeen = type.sealedSubclasses.minus(seen.map { it::class }.toSet()).toList().sortedBy { it.simpleName }
      when {
        notSeen.isEmpty() -> seen
        else -> throw InvalidStateMachine(
          "Did not encounter [${notSeen.map { it.simpleName }.joinToString(", ")}]"
        )
      }
    }

  private fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> walkTree(
    current: S,
    statesSeen: Set<S> = emptySet()
  ): Result<Set<S>> = runCatching {
    when {
      statesSeen.contains(current) -> statesSeen
      current.subsequentStates.isEmpty() -> statesSeen.plus(current)
      else -> current.subsequentStates.flatMap {
        walkTree(it, statesSeen.plus(current)).getOrThrow()
      }.toSet()
    }
  }

  @Suppress("UNCHECKED_CAST") 
  private fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> baseType(s: S): KClass<out S> = s::class.allSuperclasses
    .find { it.superclasses.contains(State::class) }!! as KClass<out S>
}

/**
 * Exception thrown when a state machine is found to be invalid during verification.
 *
 * This typically occurs when there are unreachable states in a sealed class hierarchy.
 *
 * @property message A description of why the state machine is invalid
 */
data class InvalidStateMachine(override val message: String) : Exception(message)
