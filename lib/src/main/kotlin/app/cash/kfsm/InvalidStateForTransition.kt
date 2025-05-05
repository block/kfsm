package app.cash.kfsm

data class InvalidStateForTransition(private val transition: Transition<*, *, *>, val value: Value<*, *, *>) : Exception(
  "Value cannot transition ${
    transition.from.set.toList().sortedBy { state -> state.toString() }.joinToString(", ", prefix = "{", postfix = "}")
  } to ${transition.to}, because it is currently ${value.state}. [id=${value.id}]"
) {
  /**
   * Get the state of the underlying value.
   * This reflects the state that could not be transitioned successfully.
   */
  inline fun <reified ID, reified V : Value<ID, V, S>, reified S : State<ID, V, S>> getState(): S? = value.state as? S
}
