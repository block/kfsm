package app.cash.kfsm

data class InvalidStateTransition(private val transition: Transition<*, *>, val value: Value<*, *>) : Exception(
  "Value cannot transition ${
    transition.from.set.toList().sortedBy { it.toString() }.joinToString(", ", prefix = "{", postfix = "}")
  } to ${transition.to}, because it is currently ${value.state}. [value=$value]"
) {
  /**
   * Get the state of the underlying value.
   * This reflects the state that could not be transitioned successfully.
   */
  inline fun <reified S: State<S>> getState(): S? = value.state as? S
}
