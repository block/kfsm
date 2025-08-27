package app.cash.kfsm

/**
 * Represents the result of a controller's decision in state transition
 */
data class ControllerResult<S, V>(
  val nextState: S,
  val transform: (V) -> V,
  val requiredInputs: List<String> = emptyList(),
  val appState: Map<String, Any> = emptyMap()
)

/**
 * Function type for controllers that determine state transitions based on value and inputs
 */
typealias Controller<S, V> = (value: V, inputs: Map<String, Any>) -> ControllerResult<S, V>
