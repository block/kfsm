package app.cash.kfsm

open class State<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
  transitionsFn: () -> Set<S>,
  private val invariants: List<Invariant<ID, V, S>> = emptyList(),
) {

  /** all states that can be transitioned to directly from this state */
  val subsequentStates: Set<S> by lazy { transitionsFn() }

  /** all states that are reachable from this state */
  val reachableStates: Set<S> by lazy { expand() }

  /**
   * Whether this state can transition to the given other state.
   */
  open fun canDirectlyTransitionTo(other: S): Boolean = subsequentStates.contains(other)

  /**
   * Whether this state could directly or indirectly transition to the given state.
   */
  open fun canEventuallyTransitionTo(other: S): Boolean = reachableStates.contains(other)

  /**
   * Ensure that the provided value meets all the declared invariants for this state.
   */
  fun validate(value: V): Result<V> =
    invariants.map { it.validate(value) }
      .firstOrNull { it.isFailure }
      ?: Result.success(value)

  private fun expand(found: Set<S> = emptySet()): Set<S> =
    subsequentStates.minus(found).flatMap {
      it.expand(subsequentStates + found) + it
    }.toSet().plus(found)
}
