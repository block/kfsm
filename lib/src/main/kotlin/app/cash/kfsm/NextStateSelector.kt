package app.cash.kfsm

/**
 * A selector that determines the appropriate next state for a transition based on the current value.
 * This allows for dynamic state selection during transitions based on runtime conditions.
 *
 * @param ID The type of the identifier used in values
 * @param V The type of values that can be transitioned, must implement [Value]
 * @param S The type of states in the state machine, must implement [State]
 */
fun interface NextStateSelector<ID, V : Value<ID, V, S>, S : State<ID, V, S>> {
  /**
   * Selects the appropriate next state for a transition based on the current value.
   *
   * @param value The current value being transitioned
   * @return The selected next state
   * @throws IllegalStateException if no valid next state can be selected
   */
  fun apply(value: V): Result<S>
}
