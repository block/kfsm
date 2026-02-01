package app.cash.kfsm.v2

/**
 * A transition defines a valid state change and the logic to decide its outcome.
 *
 * Transitions are the core building blocks of the state machine. Each transition:
 * - Declares which states it can originate from ([from])
 * - Declares the target state ([to])
 * - Contains a pure [decide] function that determines effects to emit
 *
 * The [decide] function is pure: it examines the current value and returns a [Decision]
 * describing the new state and any effects to emit. Effects are stored in the transactional
 * outbox and executed later, ensuring reliable delivery.
 *
 * Example:
 * ```kotlin
 * class ConfirmOrder(private val paymentId: String) : OrderTransition(
 *   from = setOf(OrderState.Pending),
 *   to = OrderState.Confirmed
 * ) {
 *   override fun decide(value: Order): Decision<OrderState, OrderEffect> =
 *     if (value.total > BigDecimal.ZERO) {
 *       Decision.accept(
 *         state = OrderState.Confirmed,
 *         effects = listOf(OrderEffect.SendConfirmationEmail(value.id, paymentId))
 *       )
 *     } else {
 *       Decision.reject("Order total must be positive")
 *     }
 * }
 *
 * // Usage
 * stateMachine.transition(order, ConfirmOrder(paymentId = "pay_123"))
 * ```
 *
 * @param ID The type of unique identifier for values
 * @param V The value type
 * @param S The state type (sealed class of all possible states)
 * @param Ef The effect type (sealed class of all possible effects)
 */
abstract class Transition<ID, V : Value<ID, V, S>, S : State<S>, Ef : Effect>(
  val from: Set<S>,
  val to: S
) {
  /**
   * Convenience constructor for transitions from a single state.
   */
  constructor(from: S, to: S) : this(setOf(from), to)

  init {
    // Validate at construction time that the transition is allowed by the state graph
    val invalidTransitions = from.filterNot { state -> state.canTransitionTo(to) }
    require(invalidTransitions.isEmpty()) {
      "Invalid transition(s): ${invalidTransitions.map { "$it -> $to" }}"
    }
  }

  /**
   * Decide the outcome of applying this transition to a value.
   *
   * This function must be pure: no side effects, only compute the decision.
   * The returned [Decision] describes:
   * - The new state (should match [to] for Accept)
   * - Effects to emit (stored in the transactional outbox)
   * - Or a rejection reason if the transition cannot proceed
   *
   * @param value The current value
   * @return A decision describing the outcome
   */
  abstract fun decide(value: V): Decision<S, Ef>

  /**
   * Check if this transition can be applied to a value in the given state.
   */
  fun canApplyTo(state: S): Boolean = from.contains(state)
}
