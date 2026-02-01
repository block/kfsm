package app.cash.kfsm.v2

import java.time.Instant
import java.util.UUID

/**
 * A message stored in the transactional outbox.
 *
 * Outbox messages are created during state transitions and stored atomically
 * with the value state change. A separate process reads these messages
 * and executes the effects, ensuring at-least-once delivery.
 *
 * @param ID The type of unique identifier for values
 * @param E The effect type
 * @property id Unique identifier for this message
 * @property valueId The ID of the value that produced this effect
 * @property effect The effect to be executed
 * @property type A string identifying the effect type, used to route effects to different processors.
 *                Defaults to the effect's simple class name.
 * @property dedupKey A key used to ensure idempotent processing. If two messages have the same
 *                    dedupKey, only the first should be processed. Useful for preventing duplicate
 *                    side effects during retries or redelivery.
 * @property dependsOnEffectId If set, this effect should not be processed until the effect with
 *                             this ID has been successfully processed. Enables ordered effect execution.
 * @property createdAt When this message was created
 * @property status Current processing status
 * @property attemptCount Number of processing attempts
 * @property lastError Error from the most recent failed attempt
 */
data class OutboxMessage<ID, E : Effect>(
  val id: String = UUID.randomUUID().toString(),
  val valueId: ID,
  val effect: E,
  val type: String = effect::class.simpleName ?: "Unknown",
  val dedupKey: String? = null,
  val dependsOnEffectId: String? = null,
  val createdAt: Instant = Instant.now(),
  val status: OutboxStatus = OutboxStatus.PENDING,
  val attemptCount: Int = 0,
  val lastError: String? = null
) {
  fun markProcessed(): OutboxMessage<ID, E> = copy(status = OutboxStatus.PROCESSED)

  fun markFailed(error: String): OutboxMessage<ID, E> = copy(
    status = OutboxStatus.FAILED,
    attemptCount = attemptCount + 1,
    lastError = error
  )

  /**
   * Check if this message is blocked by an unprocessed dependency.
   *
   * @param isDependencyProcessed A function that checks if the dependency has been processed
   * @return true if this message can be processed (no dependency or dependency is processed)
   */
  fun canProcess(isDependencyProcessed: (String) -> Boolean): Boolean =
    dependsOnEffectId == null || isDependencyProcessed(dependsOnEffectId)
}

/**
 * Processing status for outbox messages.
 */
enum class OutboxStatus {
  /** Message is waiting to be processed. */
  PENDING,

  /** Message was processed successfully. */
  PROCESSED,

  /** Message failed and may be retried. */
  FAILED,

  /**
   * Message has exceeded max retries and will not be retried automatically.
   * Requires manual intervention or a separate dead letter handler.
   */
  DEAD_LETTER
}
