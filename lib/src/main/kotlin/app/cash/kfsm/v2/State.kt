package app.cash.kfsm.v2

/**
 * Base class for defining states in a finite state machine.
 *
 * States define the allowed transitions in the state graph. Each state knows
 * which states it can transition to directly.
 *
 * To define invariants that must hold while a value is in a state, implement
 * [StateWithInvariants] on your sealed state class.
 *
 * Example:
 * ```kotlin
 * sealed class OrderState : State<OrderState>(), StateWithInvariants<Order> {
 *   // Default: no invariants
 *   override fun invariants(): List<Invariant<Order>> = emptyList()
 *
 *   data object Pending : OrderState() {
 *     override fun transitions() = setOf(Confirmed, Cancelled)
 *   }
 *   data object Confirmed : OrderState() {
 *     override fun transitions() = setOf(Shipped, Cancelled)
 *     override fun invariants() = listOf(
 *       invariant("Order must have payment reference") { order ->
 *         order.paymentReference != null
 *       }
 *     )
 *   }
 *   data object Shipped : OrderState() {
 *     override fun transitions() = setOf(Delivered)
 *     override fun invariants() = listOf(
 *       invariant("Shipped orders must have tracking number") { order ->
 *         order.trackingNumber != null
 *       }
 *     )
 *   }
 *   data object Delivered : OrderState() {
 *     override fun transitions() = emptySet()
 *   }
 *   data object Cancelled : OrderState() {
 *     override fun transitions() = emptySet()
 *   }
 * }
 * ```
 *
 * @param S The sealed class type representing all possible states
 */
abstract class State<S : State<S>> {
  /**
   * Returns the set of states that can be reached directly from this state.
   */
  abstract fun transitions(): Set<S>

  /**
   * The set of states that can be reached directly from this state through a single transition.
   */
  val subsequentStates: Set<S> by lazy { transitions() }

  /**
   * Whether this state is a terminal (final) state with no outgoing transitions.
   */
  val isTerminal: Boolean get() = subsequentStates.isEmpty()

  /**
   * The set of all states that can eventually be reached from this state through any number of transitions.
   */
  val reachableStates: Set<S> by lazy { computeReachableStates() }

  /**
   * Checks if this state can transition directly to another state.
   *
   * Override this method to allow transitions to states that cannot be
   * represented in [transitions] (e.g., data classes with parameters).
   */
  open fun canTransitionTo(other: S): Boolean = subsequentStates.contains(other)

  /**
   * Checks if this state can eventually reach another state through any number of transitions.
   *
   * @param other The state to check if we can eventually reach
   * @return true if the state is reachable, false otherwise
   */
  open fun canEventuallyTransitionTo(other: S): Boolean = reachableStates.contains(other)

  private fun computeReachableStates(): Set<S> {
    val visited = mutableSetOf<S>()
    val queue = ArrayDeque(subsequentStates)

    while (queue.isNotEmpty()) {
      val current = queue.removeFirst()
      if (current !in visited) {
        visited.add(current)
        queue.addAll(current.subsequentStates.filterNot { it in visited })
      }
    }

    return visited
  }
}
