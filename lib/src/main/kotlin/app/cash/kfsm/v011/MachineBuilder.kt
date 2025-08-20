package app.cash.kfsm.v011

import app.cash.kfsm.State
import app.cash.kfsm.Transition
import app.cash.kfsm.Value

class MachineBuilder<ID, V : Value<ID, V, S>, S : State<ID, V, S>> {
  private val transitionMap = mutableMapOf<S, Map<S, Effect<ID, V, S>>>()

  class TransitionBuilder<ID, V, S> internal constructor(
    private val from: S
  )
    where V : Value<ID, V, S>,
          S : State<ID, V, S> {
    private val transitions = mutableMapOf<S, Effect<ID, V, S>>()

    infix fun S.via(effect: Effect<ID, V, S>) {
      transitions[this@via] = effect
    }

    infix fun S.via(effect: (V) -> V): Unit = via(Effect { runCatching { effect(it) } })

    infix fun S.via(transition: Transition<ID, V, S>) {
      require(transition.from.set.contains(from) && transition.to == this@via) {
        "Expected a transition allowing $from to ${this@via}, but got $transition, " +
          "which allows only ${transition.from.set} to ${transition.to}"
      }
      return via(transition::apply)
    }

    fun build(): Map<S, Effect<ID, V, S>> = transitions
  }

  infix fun S.becomes(block: TransitionBuilder<ID, V, S>.() -> Unit) {
    transitionMap[this@becomes] = TransitionBuilder<ID, V, S>(this@becomes).apply(block).build()
  }

  fun build(): StateMachine<ID, V, S> = StateMachine(transitionMap)
}

inline fun <reified ID, V : Value<ID, V, S>, S : State<ID, V, S>> fsm(
  noinline block: MachineBuilder<ID, V, S>.() -> Unit
): StateMachine<ID, V, S> = MachineBuilder<ID, V, S>().apply(block).build()
