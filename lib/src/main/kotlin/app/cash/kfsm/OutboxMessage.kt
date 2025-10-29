package app.cash.kfsm

/**
 * Represents a message stored in the transactional outbox.
 *
 * Outbox messages capture side effects that should be executed after a state transition
 * has been successfully persisted to the database. By storing these messages in the same
 * transaction as the state change, we ensure that effects are never lost and will eventually
 * be processed (at-least-once delivery semantics).
 *
 * @param ID The type of unique identifier for values
 * @property id Unique identifier for this outbox message
 * @property valueId The ID of the value that was transitioned
 * @property effectPayload The serialized effect to be executed
 * @property createdAt Timestamp when the message was created (epoch milliseconds)
 * @property processedAt Timestamp when the message was processed (epoch milliseconds), null if not yet processed
 * @property status Current processing status of the message
 * @property attemptCount Number of processing attempts (for retry logic)
 * @property lastError Error message from the most recent failed attempt, if any
 */
data class OutboxMessage<ID>(
    val id: String,
    val valueId: ID,
    val effectPayload: EffectPayload,
    val createdAt: Long,
    val processedAt: Long? = null,
    val status: OutboxStatus = OutboxStatus.PENDING,
    val attemptCount: Int = 0,
    val lastError: String? = null
)
