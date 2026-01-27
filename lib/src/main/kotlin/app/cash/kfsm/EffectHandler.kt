package app.cash.kfsm

/**
 * Result of executing an effect.
 *
 * Effects can either:
 * - Produce a follow-up transition to apply (continuing the workflow)
 * - Complete without a follow-up transition (terminal effects like notifications)
 *
 * @param ID The type of unique identifier for values
 * @param V The value type
 * @param S The state type
 * @param Ef The effect type
 */
sealed class EffectOutcome<out ID, out V, out S, out Ef> 
  where V : Value<@UnsafeVariance ID, @UnsafeVariance V, @UnsafeVariance S>, 
        S : State<@UnsafeVariance S>,
        Ef : Effect {
  /**
   * Effect completed and produced a follow-up transition to apply.
   *
   * The transition will be applied to the value, potentially
   * triggering further state changes.
   */
  data class TransitionProduced<ID, V : Value<ID, V, S>, S : State<S>, Ef : Effect>(
    val valueId: ID,
    val transition: Transition<ID, V, S, Ef>
  ) : EffectOutcome<ID, V, S, Ef>()

  /**
   * Effect completed successfully but does not produce a follow-up transition.
   *
   * Use for terminal effects like notifications, logging, or cleanup
   * that don't need to trigger further state transitions.
   */
  data object Completed : EffectOutcome<Nothing, Nothing, Nothing, Nothing>()

  /**
   * Effect failed permanently and the workflow should transition to a failed state.
   *
   * Use this when:
   * - An RPC call failed after all client-side retries are exhausted
   * - A validation or business rule permanently prevents the effect from succeeding
   * - The failure is not recoverable and should not be retried at the outbox level
   *
   * The provided transition will be applied to move the entity to a failed/error state.
   * The outbox message will be marked as PROCESSED (not FAILED) since the failure
   * was handled by transitioning to an error state.
   *
   * Example:
   * ```kotlin
   * is OrderEffect.ProcessPayment -> {
   *   try {
   *     val paymentId = paymentService.charge(effect.amount) // has internal retries
   *     EffectOutcome.TransitionProduced(valueId, PaymentReceived(paymentId))
   *   } catch (e: PaymentPermanentlyDeclinedException) {
   *     EffectOutcome.FailedWithTransition(valueId, PaymentFailed(e.reason))
   *   }
   * }
   * ```
   */
  data class FailedWithTransition<ID, V : Value<ID, V, S>, S : State<S>, Ef : Effect>(
    val valueId: ID,
    val transition: Transition<ID, V, S, Ef>,
    val reason: String
  ) : EffectOutcome<ID, V, S, Ef>()
}

/**
 * Handler that executes effects and optionally returns transitions to continue the workflow.
 *
 * Effects can either:
 * - Return [EffectOutcome.TransitionProduced] with a transition to apply
 * - Return [EffectOutcome.Completed] for terminal effects
 * - Return [Result.failure] if the effect execution failed
 *
 * Implementations should be:
 * - **Idempotent**: Effects may be retried on failure
 * - **Total**: Handle all effect types, return Result.failure for errors instead of throwing
 *
 * Example:
 * ```kotlin
 * class OrderEffectHandler(
 *   private val paymentService: PaymentService
 * ) : EffectHandler<String, Order, OrderState, OrderEffect> {
 *
 *   override fun handle(
 *     valueId: String, 
 *     effect: OrderEffect
 *   ): Result<EffectOutcome<String, Order, OrderState, OrderEffect>> =
 *     when (effect) {
 *       is OrderEffect.ProcessPayment -> runCatching {
 *         val paymentId = paymentService.charge(effect.amount)
 *         EffectOutcome.TransitionProduced(valueId, PaymentReceived(paymentId))
 *       }
 *
 *       is OrderEffect.SendNotification -> runCatching {
 *         notificationService.send(effect.message)
 *         EffectOutcome.Completed
 *       }
 *     }
 * }
 * ```
 *
 * @param ID The type of unique identifier for values
 * @param V The value type
 * @param S The state type
 * @param Ef The effect type
 */
fun interface EffectHandler<ID, V : Value<ID, V, S>, S : State<S>, Ef : Effect> {
  /**
   * Execute the effect and return the outcome.
   *
   * @param valueId The ID of the value that produced this effect
   * @param effect The effect to execute
   * @return Success with an [EffectOutcome], or failure if execution failed
   */
  fun handle(valueId: ID, effect: Ef): Result<EffectOutcome<ID, V, S, Ef>>
}
