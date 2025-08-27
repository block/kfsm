package app.cash.kfsm

/**
 * DSL marker for state machine building context
 */
@DslMarker
annotation class StateMachineDsl

/**
 * Builder for state transitions with controller support
 */
@StateMachineDsl
class TransitionBuilder<S : Any, V : Any> {
  private val transitions = mutableMapOf<S, (V) -> V>()
  
  infix fun S.via(transform: (V) -> V) {
    transitions[this] = transform
  }
  
  internal fun build() = transitions.toMap()
}

/**
 * Builder for state definitions with controller support
 */
@StateMachineDsl
class StateBuilder<S : Any, V : Any> {
  private val stateTransitions = mutableMapOf<S, Pair<Any, Map<S, (V) -> V>>>()

  fun S.becomes(
    selector: (() -> Result<S>)? = null,
    controller: Controller<S, V>? = null,
    builder: TransitionBuilder<S, V>.() -> Unit
  ) {
    require((selector == null) xor (controller == null)) { "Must provide either selector or controller, not both" }
    val transitionBuilder = TransitionBuilder<S, V>()
    builder(transitionBuilder)
    stateTransitions[this] = Pair(
      selector ?: controller!!,
      transitionBuilder.build()
    )
  }

  internal fun build(): Map<S, Pair<Any, Map<S, (V) -> V>>> = stateTransitions.toMap()
}

/**
 * Creates a state machine with either selector or controller-based transitions
 */
fun <S : Any, V : Any> fsm(builder: StateBuilder<S, V>.() -> Unit): StateMachine<S, V> {
  val stateBuilder = StateBuilder<S, V>()
  builder(stateBuilder)
  val states = stateBuilder.build()
  
  // Determine if we're building a selector or controller based machine
  val firstTransition = states.values.firstOrNull()?.first
  return when {
    firstTransition is Function0<*> -> SelectorStateMachine(states.mapValues { it.value.first as () -> Result<S> to it.value.second })
    firstTransition is Controller<*, *> -> ControllerStateMachine(states.mapValues { it.value.first as Controller<S, V> to it.value.second })
    else -> throw IllegalStateException("Invalid state machine configuration")
  }
}
