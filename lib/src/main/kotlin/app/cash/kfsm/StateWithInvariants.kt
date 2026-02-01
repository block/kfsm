package app.cash.kfsm

/**
 * Interface for states that define type-safe invariants.
 *
 * Implement this interface on your sealed state class to get type-safe invariant
 * validation without the ugly casts required by [State.invariants].
 *
 * Example:
 * ```kotlin
 * sealed class OrderState : State<OrderState>(), StateWithInvariants<Order> {
 *   // Default: no invariants
 *   override fun invariants(): List<Invariant<Order>> = emptyList()
 *
 *   data object Confirmed : OrderState() {
 *     override fun transitions() = setOf(Shipped, Cancelled)
 *     override fun invariants() = listOf(
 *       invariant("Order must have payment reference") { order ->
 *         order.paymentReference != null
 *       }
 *     )
 *   }
 * }
 * ```
 *
 * The [StateMachine] automatically detects when a state implements this interface
 * and uses the typed invariants for validation.
 *
 * @param V The value type for invariant validation
 */
interface StateWithInvariants<V> {
  /**
   * Returns the list of invariants that must hold true while a value is in this state.
   *
   * @return List of invariants for this state (empty by default)
   */
  fun invariants(): List<Invariant<V>>
}

/**
 * Validates typed invariants if the state implements [StateWithInvariants].
 *
 * @param value The value to validate
 * @return Success with Unit if all invariants pass, or failure with the first violation
 */
fun <V> validateTypedInvariants(state: Any, value: V): Result<Unit> {
  @Suppress("UNCHECKED_CAST")
  val typedState = state as? StateWithInvariants<V> ?: return Result.success(Unit)
  
  for (invariant in typedState.invariants()) {
    val result = invariant.validate(value)
    if (result.isFailure) {
      return result
    }
  }
  return Result.success(Unit)
}
