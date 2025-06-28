package app.cash.kfsm

/**
 * Base class for defining states in a finite state machine.
 *
 * States are the foundation of kFSM. Each state:
 * - Knows which states it can transition to directly
 * - Can validate invariants that must hold while in this state
 * - Can determine paths to other reachable states
 *
 * Example:
 * ```kotlin
 * sealed class TrafficLightState : State<String, TrafficLight, TrafficLightState>
 * object Red : TrafficLightState(() -> setOf(Green))
 * object Yellow : TrafficLightState(() -> setOf(Red))
 * object Green : TrafficLightState(() -> setOf(Yellow))
 * ```
 *
 * @param ID The type used to identify values (often String or Long)
 * @param V The type of values that can be in this state
 * @param S The sealed class type representing all possible states
 * @property transitionsFn Function that returns the set of states this state can transition to
 * @property invariants List of conditions that must hold true while in this state
 */
open class State<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
  transitionsFn: () -> Set<S>,
  private val invariants: List<Invariant<ID, V, S>> = emptyList(),
) {

  /**
   * The set of states that can be reached directly from this state through a single transition.
   */
  val subsequentStates: Set<S> by lazy { transitionsFn() }

  /**
   * The set of all states that can eventually be reached from this state through any number of transitions.
   */
  val reachableStates: Set<S> by lazy { expand() }

  /**
   * Checks if this state can transition directly to another state in a single step.
   *
   * @param other The state to check if we can transition to
   * @return true if a direct transition is possible, false otherwise
   */
  open fun canDirectlyTransitionTo(other: S): Boolean = subsequentStates.contains(other)

  /**
   * Checks if this state can eventually reach another state through any number of transitions.
   *
   * @param other The state to check if we can eventually reach
   * @return true if the state is reachable, false otherwise
   */
  open fun canEventuallyTransitionTo(other: S): Boolean = reachableStates.contains(other)

  /**
   * Validates that a value meets all invariants defined for this state.
   *
   * Invariants are conditions that must hold true while a value is in this state.
   * This method checks all invariants and returns the first failure encountered,
   * or success if all invariants pass.
   *
   * @param value The value to validate against this state's invariants
   * @return A Result containing the value if valid, or the first failure encountered
   */
  fun validate(value: V): Result<V> =
    invariants.map { it.validate(value) }
      .firstOrNull { it.isFailure }
      ?: Result.success(value)

  /**
   * Finds the shortest path to reach a target state from this state.
   *
   * Uses a breadth-first search algorithm to find the shortest sequence of states
   * that leads from this state to the target state. The path includes both the
   * starting and ending states.
   *
   * Example:
   * ```kotlin
   * // Given states A -> B -> C
   * val path = A.shortestPathTo(C) // Returns [A, B, C]
   * ```
   *
   * @param to The target state to reach
   * @return A list of states representing the shortest path, or empty if no path exists
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
