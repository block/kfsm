package app.cash.kfsm

/**
 * Base class for defining states in a finite state machine.
 *
 * States define the allowed transitions in the state graph. Each state knows
 * which states it can transition to directly and can define invariants that
 * must hold true while a value is in that state.
 *
 * Example:
 * ```kotlin
 * sealed class OrderState : State<OrderState>() {
 *   data object Pending : OrderState() {
 *     override fun transitions() = setOf(Confirmed, Cancelled)
 *   }
 *   data object Confirmed : OrderState() {
 *     override fun transitions() = setOf(Shipped, Cancelled)
 *     override fun <V> invariants() = listOf(
 *       invariant("Order must have payment reference") { v: V ->
 *         (v as? Order)?.paymentReference != null
 *       }
 *     )
 *   }
 *   data object Shipped : OrderState() {
 *     override fun transitions() = setOf(Delivered)
 *     override fun <V> invariants() = listOf(
 *       invariant("Shipped orders must have tracking number") { v: V ->
 *         (v as? Order)?.trackingNumber != null
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
   * Returns the list of invariants that must hold true while a value is in this state.
   *
   * Override this method to define state-specific invariants. Invariants are validated
   * after a transition completes, and if any invariant fails, the transition is rejected.
   *
   * @return List of invariants for this state (empty by default)
   */
  open fun <V> invariants(): List<Invariant<V>> = emptyList()

  /**
   * The set of states that can be reached directly from this state through a single transition.
   */
  val subsequentStates: Set<S> by lazy { transitions() }

  /**
   * Checks if this state can transition directly to another state.
   *
   * Override this method to allow transitions to states that cannot be
   * represented in [transitions] (e.g., data classes with parameters).
   */
  open fun canTransitionTo(other: S): Boolean = subsequentStates.contains(other)

  /**
   * Validates that a value satisfies all invariants defined for this state.
   *
   * @param value The value to validate
   * @return Success with Unit if all invariants pass, or failure with the first violation
   */
  fun <V> validateInvariants(value: V): Result<Unit> {
    val stateInvariants: List<Invariant<V>> = invariants()
    for (invariant in stateInvariants) {
      val result = invariant.validate(value)
      if (result.isFailure) {
        return result
      }
    }
    return Result.success(Unit)
  }
}
