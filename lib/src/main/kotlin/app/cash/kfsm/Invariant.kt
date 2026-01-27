package app.cash.kfsm

/**
 * Represents an invariant (condition) that must hold true for a value in a particular state.
 *
 * Invariants are validated after a state transition completes. If an invariant fails,
 * the transition is rejected and the state change is not persisted.
 *
 * Invariants help ensure data consistency by encoding business rules that must hold
 * true while a value is in a given state. For example:
 * - "In the Paid state, the payment amount must be greater than 0"
 * - "In the Shipped state, a tracking number must be present"
 *
 * Example:
 * ```kotlin
 * sealed class OrderState : State<OrderState>() {
 *   data object Paid : OrderState() {
 *     override fun transitions() = setOf(Shipped, Refunded)
 *     override fun invariants() = listOf(
 *       invariant("Payment amount must be positive") { order: Order ->
 *         order.paymentAmount > BigDecimal.ZERO
 *       }
 *     )
 *   }
 * }
 * ```
 *
 * @param V The type of value being validated
 */
fun interface Invariant<V> {
  /**
   * Validates that the given value satisfies this invariant.
   *
   * @param value The value to validate
   * @return A [Result] containing Unit if valid, or an [InvariantViolation] if not
   */
  fun validate(value: V): Result<Unit>
}

/**
 * Creates an invariant from a predicate function.
 *
 * @param message Description of what the invariant checks (used in error messages)
 * @param predicate Function that returns true if the invariant holds
 * @return An [Invariant] that validates using the predicate
 */
fun <V> invariant(message: String, predicate: (V) -> Boolean): Invariant<V> =
  Invariant { value ->
    if (predicate(value)) {
      Result.success(Unit)
    } else {
      Result.failure(InvariantViolation(message))
    }
  }

/**
 * Exception thrown when a value fails to satisfy a state invariant.
 */
class InvariantViolation(
  val invariantMessage: String
) : Exception("Invariant violation: $invariantMessage")
