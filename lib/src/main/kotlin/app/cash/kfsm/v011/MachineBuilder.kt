package app.cash.kfsm.v011

import app.cash.kfsm.State
import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import app.cash.kfsm.Value

/**
 * Default do-nothing transitioner that can be used when no special transition behavior is needed.
 */
class DefaultTransitioner<ID, T : Transition<ID, V, S>, V : Value<ID, V, S>, S : State<ID, V, S>> :
  Transitioner<ID, T, V, S>()

/**
 * Builder class for creating state machines with type-safe transitions.
 *
 * @param ID The type of the identifier used in the state machine
 * @param V The type of value being transformed, must implement [Value]
 * @param S The type of state in the state machine, must implement [State]
 */
class MachineBuilder<ID, V : Value<ID, V, S>, S : State<ID, V, S>> {
  private val transitions = mutableMapOf<S, Map<S, Transition<ID, V, S>>>()

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
    private val transitions = mutableMapOf<S, Transition<ID, V, S>>()

    /**
     * Defines a transition to a target state with a given effect.
     *
     * @param effect The effect to apply during the transition
     * @throws IllegalStateException if a transition to this state is already defined
     * @throws IllegalStateException if the from state cannot transition to this state
     */
    infix fun S.via(effect: Effect<ID, V, S>) {
      if (this@via in transitions) {
        throw IllegalStateException("State $this already has a transition defined from $from")
      }
      if (!from.canDirectlyTransitionTo(this@via)) {
        throw IllegalStateException(
          "State $from declares that it cannot transition to $this. Either the fsm declaration or the State is incorrect"
        )
      }
      transitions[this@via] = EffectTransition(from, this@via, effect)
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
      transitions[this@via] = transition
    }

    internal fun build(): Map<S, Transition<ID, V, S>> = transitions
  }

  /**
   * Defines transitions from a given state using a builder block.
   *
   * @param block A builder block that defines the possible transitions from this state
   * @throws IllegalStateException if transitions for this state have already been defined
   */
  infix fun S.becomes(block: TransitionBuilder<ID, V, S>.() -> Unit) {
    if (this@becomes in transitions) {
      throw IllegalStateException("State $this has multiple `becomes` blocks defined")
    }
    transitions[this@becomes] = TransitionBuilder<ID, V, S>(this@becomes).apply(block).build()
  }

  fun build(transitioner: Transitioner<ID, Transition<ID, V, S>, V, S>): StateMachine<ID, V, S> =
    StateMachine(transitions, transitioner)
}

/**
 * Creates a new state machine using a builder DSL.
 *
 * @param ID The type of the identifier used in the state machine
 * @param V The type of value being transformed
 * @param S The type of state in the state machine
 * @param transitioner The transitioner to use for state transitions. If not provided, a default do-nothing transitioner will be used.
 * @param block A builder block that defines the state machine's transitions
 * @return A new [StateMachine] instance
 */
inline fun <reified ID, V : Value<ID, V, S>, S : State<ID, V, S>> fsm(
  transitioner: Transitioner<ID, Transition<ID, V, S>, V, S> = DefaultTransitioner(),
  noinline block: MachineBuilder<ID, V, S>.() -> Unit
): Result<StateMachine<ID, V, S>> = runCatching { MachineBuilder<ID, V, S>().apply(block).build(transitioner) }

/**
 * A transition between two states with an associated effect. This internal class exists so that it is possible to store
 * a simple Effect as a Transition inside the state machine.
 */
internal class EffectTransition<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
  from: S,
  to: S,
  private val effect: Effect<ID, V, S>
) : Transition<ID, V, S>(from, to) {
  override fun effect(value: V): Result<V> = effect.apply(value)
}
