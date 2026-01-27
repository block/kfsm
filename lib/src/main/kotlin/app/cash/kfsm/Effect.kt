package app.cash.kfsm

/**
 * Represents a side effect to be executed after a state transition.
 *
 * Effects are produced by the reducer as part of a [Decision] and are stored
 * in the transactional outbox alongside the state change. This ensures that
 * effects are never lost and will eventually be processed (at-least-once
 * delivery semantics).
 *
 * Effects should be:
 * - Serializable (for storage in the outbox)
 * - Idempotent (since they may be retried)
 * - Self-contained (all data needed to execute the effect)
 *
 * Example:
 * ```kotlin
 * sealed class OrderEffect : Effect {
 *   data class SendConfirmationEmail(val orderId: String, val email: String) : OrderEffect()
 *   data class NotifyWarehouse(val orderId: String, val items: List<Item>) : OrderEffect()
 *   data class RefundPayment(val orderId: String, val amount: Money) : OrderEffect()
 * }
 * ```
 */
interface Effect
