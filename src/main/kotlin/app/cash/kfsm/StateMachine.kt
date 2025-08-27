package app.cash.kfsm

/**
 * Interface for state machines
 */
interface StateMachine<S : Any, V : Any> {
  fun transition(state: S, value: V): TransitionResult<S, V>
}

/**
 * Result of a state transition
 */
data class TransitionResult<S : Any, V : Any>(
  val nextState: S,
  val value: V,
  val requiredInputs: List<String> = emptyList(),
  val appState: Map<String, Any> = emptyMap()
)

/**
 * Implementation of state machine using selectors
 */
class SelectorStateMachine<S : Any, V : Any>(
  private val states: Map<S, Pair<() -> Result<S>, Map<S, (V) -> V>>>
) : StateMachine<S, V> {
  override fun transition(state: S, value: V): TransitionResult<S, V> {
    val (selector, transitions) = states[state] ?: throw IllegalStateException("Unknown state: $state")
    val nextState = selector().getOrThrow()
    val transform = transitions[nextState] ?: throw IllegalStateException("Invalid transition to $nextState")
    return TransitionResult(nextState, transform(value))
  }
}

/**
 * Implementation of state machine using controllers
 */
class ControllerStateMachine<S : Any, V : Any>(
  private val states: Map<S, Pair<Controller<S, V>, Map<S, (V) -> V>>>
) : StateMachine<S, V> {
  fun transition(state: S, value: V, inputs: Map<String, Any>): TransitionResult<S, V> {
    val (controller, transitions) = states[state] ?: throw IllegalStateException("Unknown state: $state")
    val result = controller(value, inputs)
    val transform = transitions[result.nextState] ?: throw IllegalStateException("Invalid transition to ${result.nextState}")
    return TransitionResult(
      result.nextState,
      result.transform(transform(value)),
      result.requiredInputs,
      result.appState
    )
  }

  override fun transition(state: S, value: V): TransitionResult<S, V> {
    return transition(state, value, emptyMap())
  }
}
