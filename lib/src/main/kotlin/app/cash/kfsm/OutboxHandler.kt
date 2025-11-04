package app.cash.kfsm

import app.cash.kfsm.annotations.ExperimentalLibraryApi

/**
 * Handles the capture of deferrable effects for storage in the transactional outbox.
 *
 * The outbox handler is responsible for creating outbox messages when deferrable effects
 * are encountered during state transitions. These messages are then passed to the
 * `persistWithOutbox` method to be stored in the same database transaction as the state change.
 *
 * Example implementation:
 * ```kotlin
 * class DatabaseOutboxHandler<ID, V : Value<ID, V, S>, S : State<ID, V, S>> : OutboxHandler<ID, V, S> {
 *
 *     private val pendingMessages = mutableListOf<OutboxMessage<ID>>()
 *
 *     override fun captureEffect(
 *         value: V,
 *         effect: DeferrableEffect<ID, V, S>
 *     ): Result<V> {
 *         val message = OutboxMessage(
 *             id = UUID.randomUUID().toString(),
 *             valueId = value.id,
 *             effectPayload = effect.serialize(),
 *             createdAt = System.currentTimeMillis()
 *         )
 *         pendingMessages.add(message)
 *         return Result.success(value)
 *     }
 *
 *     override fun getPendingMessages(): List<OutboxMessage<ID>> = pendingMessages.toList()
 *
 *     override fun clearPending() {
 *         pendingMessages.clear()
 *     }
 * }
 * ```
 *
 * @param ID The type of unique identifier for values
 * @param V The type of value being transitioned
 * @param S The type of state
 */
@ExperimentalLibraryApi
interface OutboxHandler<ID, V : Value<ID, V, S>, S : State<ID, V, S>> {
    /**
     * Capture an effect for later execution.
     *
     * This method is called during a state transition when a deferrable effect is encountered.
     * Instead of executing the effect immediately, it should be serialized and stored for
     * later processing.
     *
     * @param value The value being transitioned
     * @param effect The deferrable effect to capture
     * @return The value unchanged (effect is not executed)
     */
    fun captureEffect(value: V, effect: DeferrableEffect<ID, V, S>): Result<V>

    /**
     * Get all pending outbox messages that have been captured since the last clear.
     *
     * This is called by the transitioner to retrieve messages that should be persisted
     * in the same transaction as the state change.
     *
     * @return List of pending outbox messages
     */
    fun getPendingMessages(): List<OutboxMessage<ID>>

    /**
     * Clear the pending messages after they have been persisted.
     *
     * This should be called after a successful transaction to reset the handler state.
     */
    fun clearPending()
}
