package app.cash.kfsm

class InvalidStateTransition(transition: Transition<*>, value: Value<*>) : Exception(
  "Value cannot transition ${
    transition.from.toList().sortedBy { it.toString() }.joinToString(", ", prefix = "{", postfix = "}")
  } to ${transition.to}, because it is currently ${value.state}"
)
