package app.cash.kfsm.v011

import app.cash.kfsm.State
import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import app.cash.kfsm.Value

class StateMachine<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
  val transitionMap: Map<S, Map<S, Transition<ID, V, S>>>,
  private val transitioner: Transitioner<ID, Transition<ID, V, S>, V, S>
) {
  /**
   * Transitions a value to the target state if a valid transition exists.
   *
   * @param value The current value to transition
   * @param targetState The desired target state
   * @return Result containing the new value after transition, or failure if transition is invalid
   */
  fun transitionTo(
    value: V,
    targetState: S
  ): Result<V> =
    transitionMap[value.state]?.get(targetState)?.let { transition ->
      transitioner.transition(value, transition)
    } ?: Result.failure(IllegalStateException("No transition defined from ${value.state} to $targetState"))
}
