package app.cash.kfsm

import app.cash.kfsm.annotations.ExperimentalLibraryApi

/**
 * Represents an effect that can be deferred and stored in an outbox for later execution.
 *
 * This interface enables the transactional outbox pattern, where side effects are captured
 * during a state transition and persisted in the same database transaction as the state change.
 * The effects are then executed asynchronously by a separate processor.
 *
 * Example:
 * ```kotlin
 * class SendEmail(
 *     from: OrderState,
 *     to: OrderState,
 *     private val recipient: String
 * ) : OrderTransition(from, to), DeferrableEffect<String, Order, OrderState> {
 *
 *     override fun effect(value: Order): Result<Order> {
 *         // This will be executed later by the outbox processor
 *         emailService.send(recipient, "Order ${value.id} status changed")
 *         return Result.success(value)
 *     }
 *
 *     override fun serialize(): EffectPayload = EffectPayload(
 *         effectType,
 *         data = Json.encodeToString(mapOf("recipient" to recipient, "orderId" to value.id))
 *     )
 *
 *     override val effectType = "send_email"
 * }
 * ```
 *
 * @param ID The type of unique identifier for values
 * @param V The type of value being transitioned
 * @param S The type of state
 */
@ExperimentalLibraryApi
interface DeferrableEffect<ID, V : Value<ID, V, S>, S : State<ID, V, S>> : Effect<ID, V, S> {
    /**
     * Serialize this effect for storage in the outbox.
     *
     * The serialized payload should contain all information necessary to execute the effect later.
     * Common serialization formats include JSON, Protocol Buffers, or Avro.
     *
     * @return The serialized effect payload
     */
    fun serialize(value: V): Result<EffectPayload>

    /**
     * A unique identifier for this type of effect.
     *
     * This is used by the effect executor to determine how to deserialize and execute the effect.
     * Should be a stable, descriptive string (e.g., "send_email", "disable_camera").
     */
    val effectType: String
}
