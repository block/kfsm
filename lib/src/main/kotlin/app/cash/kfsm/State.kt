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

  /**
   * Finds the shortest path to a given state using a naive recursive algorithm.
   *
   * Note: includes this state by default.
   *
   * ```kotlin
   * // Given a simple state machine: [A] -> [B] -> [C]
   * A.shortestPathTo(C) // listOf(A, B, C)
   * A.shortestPathTo(A) // listOf(A)
   * ```
   *
   * @param to The state to find a path to.
   * @return a list of states making up the shortest path.
   */
  @Suppress("UNCHECKED_CAST")
  fun shortestPathTo(to: S): List<S> {
    val start = this as S

    if (this == to) return listOf(start)
    if (!canEventuallyTransitionTo(to)) return emptyList()

    val initialQueue = ArrayDeque(listOf(start))
    val predecessor = mutableMapOf<S, S?>(start to null)

    tailrec fun bfs(queue: ArrayDeque<S>): List<S> {
      if (queue.isEmpty()) return emptyList()

      val current = queue.removeFirst()

      if (current == to) {
        val path = generateSequence(to) { predecessor[it] }
          .toList()
          .asReversed()
        return path
      }

      current.subsequentStates.forEach { next ->
        if (next !in predecessor) {
          predecessor[next] = current
          queue += next
        }
      }

      return bfs(queue)
    }

    return bfs(initialQueue)
  }

  private fun expand(found: Set<S> = emptySet()): Set<S> =
    subsequentStates.minus(found).flatMap {
      it.expand(subsequentStates + found) + it
    }.toSet().plus(found)
}
