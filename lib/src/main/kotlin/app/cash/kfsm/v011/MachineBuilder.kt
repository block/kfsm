package app.cash.kfsm.v011

import app.cash.kfsm.State
import app.cash.kfsm.Transition
import app.cash.kfsm.Value

/**
 * Builder class for creating state machines with type-safe transitions.
 *
 * @param ID The type of the identifier used in the state machine
 * @param V The type of value being transformed, must implement [Value]
 * @param S The type of state in the state machine, must implement [State]
 */
class MachineBuilder<ID, V : Value<ID, V, S>, S : State<ID, V, S>> {
  private val transitionMap = mutableMapOf<S, Map<S, Effect<ID, V, S>>>()

  /**
   * Builder class for defining transitions from a specific state.
   *
   * @param ID The type of the identifier used in the state machine
   * @param V The type of value being transformed
   * @param S The type of state in the state machine
   * @property from The state from which transitions are being defined
   */
  class TransitionBuilder<ID, V, S> internal constructor(
    private val from: S
  )
    where V : Value<ID, V, S>,
          S : State<ID, V, S> {
    private val transitions = mutableMapOf<S, Effect<ID, V, S>>()

    /**
     * Defines a transition to a target state with a given effect.
     *
     * @param effect The effect to apply during the transition
     */
    infix fun S.via(effect: Effect<ID, V, S>) {
      transitions[this@via] = effect
    }

    /**
     * Defines a transition to a target state with a simple value transformation function.
     *
     * @param effect A function that transforms the value during the transition
     */
    infix fun S.via(effect: (V) -> V): Unit = via(Effect { runCatching { effect(it) } })

    /**
     * Defines a transition using a predefined [Transition] instance.
     *
     * @param transition The transition instance to use
     * @throws IllegalArgumentException if the transition's from/to states don't match the current context
     */
    infix fun S.via(transition: Transition<ID, V, S>) {
      require(transition.from.set.contains(from) && transition.to == this@via) {
        "Expected a transition allowing $from to ${this@via}, but got $transition, " +
          "which allows only ${transition.from.set} to ${transition.to}"
      }
      return via(transition::apply)
    }

    internal fun build(): Map<S, Effect<ID, V, S>> = transitions
  }

  /**
   * Defines transitions from a given state using a builder block.
   *
   * @param block A builder block that defines the possible transitions from this state
   * @throws IllegalStateException if transitions for this state have already been defined
   */
  infix fun S.becomes(block: TransitionBuilder<ID, V, S>.() -> Unit) {
    if (this@becomes in transitionMap) {
      throw IllegalStateException("State $this has multiple `becomes` blocks defined")
    }
    transitionMap[this@becomes] = TransitionBuilder<ID, V, S>(this@becomes).apply(block).build()
  }

  /**
   * Builds and returns a [StateMachine] instance with the defined transitions.
   *
   * @return A new [StateMachine] instance
   */
  fun build(): StateMachine<ID, V, S> = StateMachine(transitionMap)
}

/**
 * Creates a new state machine using a builder DSL.
 *
 * @param ID The type of the identifier used in the state machine
 * @param V The type of value being transformed
 * @param S The type of state in the state machine
 * @param block A builder block that defines the state machine's transitions
 * @return A new [StateMachine] instance
 */
inline fun <reified ID, V : Value<ID, V, S>, S : State<ID, V, S>> fsm(
  noinline block: MachineBuilder<ID, V, S>.() -> Unit
): Result<StateMachine<ID, V, S>> = runCatching { MachineBuilder<ID, V, S>().apply(block).build() }
