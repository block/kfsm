package app.cash.kfsm

class NoPathToTargetState(val value: Value<*, *, *>, val targetState: State<*, *, *>) : Exception(
  "No transition path exists from ${value.state} to $targetState for $value"
)
