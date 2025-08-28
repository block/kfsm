package app.cash.kfsm

class StateMachine<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
  val transitionMap: Map<S, Map<S, Transition<ID, V, S>>>,
  private val selectors: Map<S, NextStateSelector<ID, V, S>>,
  private val transitioner: Transitioner<ID, Transition<ID, V, S>, V, S>
) {
  /**
   * Returns all available transitions from a given state.
   *
   * @param state The current state
   * @return Set of all possible transitions from the given state
   */
  fun getAvailableTransitions(state: S): Set<Transition<ID, V, S>> = transitionMap[state]?.values?.toSet() ?: emptySet()

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
    } ?: Result.failure(NoPathToTargetState(value, targetState))

  /**
   * Advances the state machine to the next state based on the current value.
   *
   * This method uses a [NextStateSelector] to determine the next appropriate state and then
   * performs the transition. The selector must be defined for the current state, and both
   * the selection and transition must be valid for the operation to succeed.
   *
   * @param value The current value to advance to its next state
   * @return Result containing the new value after transition, or failure if:
   *         - No selector is defined for the current state
   *         - The selector fails to determine a valid next state
   *         - The transition to the selected state is invalid
   */
  fun advance(value: V): Result<V> =
    runCatching {
      val selector = selectors[value.state] ?: throw IllegalStateException("No selector for state ${value.state}")
      val to = selector.apply(value).getOrThrow()
      transitionTo(value, to).getOrThrow()
    }

  /**
   * Generates a Mermaid markdown state diagram representation of this state machine.
   *
   * The diagram shows all states and their possible transitions, making it easy to
   * visualize and document the state machine's structure.
   *
   * Example output:
   * ```mermaid
   * stateDiagram-v2
   *     [*] --> InitialState
   *     InitialState --> NextState
   *     NextState --> FinalState
   * ```
   *
   * @param initialState The initial state to mark as the entry point
   * @return A string containing the Mermaid markdown diagram
   */
  fun mermaidStateDiagramMarkdown(initialState: S): String {
    val transitions =
      transitionMap
        .flatMap { (fromState, targets) ->
          targets.keys.map { toState ->
            "${fromState::class.simpleName} --> ${toState::class.simpleName}"
          }
        }.distinct()
        .sorted()

    return listOf(
      "stateDiagram-v2",
      "[*] --> ${initialState::class.simpleName}"
    ).plus(transitions).joinToString("\n    ")
  }
}
