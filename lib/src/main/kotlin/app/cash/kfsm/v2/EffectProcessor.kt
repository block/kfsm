package app.cash.kfsm.v2

/**
 * Result of processing an effect.
 */
sealed class ProcessingResult<out V> {
  /**
   * Effect was processed and a state transition occurred.
   */
  data class StateTransitioned<V>(val value: V) : ProcessingResult<V>()

  /**
   * Effect was processed but no state transition was needed (terminal effect).
   */
  data object EffectCompleted : ProcessingResult<Nothing>()

  /**
   * Effect failed permanently and the workflow transitioned to a failed state.
   *
   * The outbox message is marked as PROCESSED (not FAILED) because the failure
   * was handled by transitioning to an error state.
   */
  data class FailedWithTransition<V>(val value: V, val reason: String) : ProcessingResult<V>()
}

/**
 * Processes effects from the transactional outbox.
 *
 * The EffectProcessor reads pending messages from the outbox, executes
 * the corresponding effects, and applies the resulting transitions back
 * to the state machine. This creates a feedback loop for multi-step workflows.
 *
 * Flow:
 * 1. Fetch pending outbox messages
 * 2. Execute each effect via the handler
 * 3. If the effect produces a transition:
 *    a. Load the value and apply the transition
 *    b. Persist the updated value (which may produce more effects)
 * 4. Mark the original message as processed
 *
 * Example:
 * ```kotlin
 * val processor = EffectProcessor(
 *   outbox = MysqlOutbox(database),
 *   handler = OrderEffectHandler(paymentService),
 *   stateMachine = orderStateMachine,
 *   valueLoader = { id -> orderRepository.findById(id) }
 * )
 *
 * // Run as a scheduled job or triggered by DB notifications
 * processor.processAll()
 * ```
 *
 * @param ID The type of unique identifier for values
 * @param V The value type
 * @param S The state type
 * @param Ef The effect type
 * @param effectTypes If provided, only process messages with these effect types.
 *                    Use this to run separate processors for different priority levels.
 * @param maxAttempts Maximum number of attempts before a message is dead-lettered.
 *                    If null, messages will be marked as FAILED indefinitely (manual retry).
 */
class EffectProcessor<ID, V : Value<ID, V, S>, S : State<S>, Ef : Effect>(
  private val outbox: Outbox<ID, Ef>,
  private val handler: EffectHandler<ID, V, S, Ef>,
  private val stateMachine: StateMachine<ID, V, S, Ef>,
  private val valueLoader: (ID) -> Result<V?>,
  private val awaitable: AwaitableStateMachine<ID, V, S, Ef>? = null,
  private val effectTypes: Set<String>? = null,
  private val maxAttempts: Int? = null
) {
  /**
   * Process all pending messages in the outbox.
   *
   * @param batchSize Maximum number of messages to process
   * @return Number of successfully processed messages
   */
  fun processAll(batchSize: Int = 100): Int {
    val pending = outbox.fetchPending(batchSize, effectTypes)
    var processed = 0

    for (message in pending) {
      val result = processOne(message)
      if (result.isSuccess) processed++
    }

    return processed
  }

  private fun processOne(message: OutboxMessage<ID, Ef>): Result<ProcessingResult<V>> {
    return handler.handle(message.valueId, message.effect)
      .flatMap { outcome ->
        when (outcome) {
          is EffectOutcome.TransitionProduced -> {
            applyTransition(outcome.valueId, outcome.transition)
          }
          is EffectOutcome.Completed -> {
            Result.success(ProcessingResult.EffectCompleted)
          }
          is EffectOutcome.FailedWithTransition -> {
            applyTransition(outcome.valueId, outcome.transition)
              .map { result -> ProcessingResult.FailedWithTransition(result.value, outcome.reason) }
          }
        }
      }
      .onSuccess { result ->
        outbox.markProcessed(message.id)
        when (result) {
          is ProcessingResult.StateTransitioned -> {
            awaitable?.markCompleted(message.valueId, result.value)
          }
          is ProcessingResult.FailedWithTransition -> {
            awaitable?.markFailed(message.valueId, WorkflowFailedException(result.reason))
          }
          is ProcessingResult.EffectCompleted -> {
            // No awaitable notification needed for terminal effects
          }
        }
      }
      .onFailure { error ->
        outbox.markFailed(message.id, error.message ?: "Unknown error", maxAttempts)
        awaitable?.markFailed(message.valueId, error)
      }
  }

  private fun applyTransition(
    valueId: ID,
    transition: Transition<ID, V, S, Ef>
  ): Result<ProcessingResult.StateTransitioned<V>> {
    return valueLoader(valueId)
      .flatMap { value ->
        if (value == null) {
          Result.failure(ValueNotFound(valueId.toString()))
        } else {
          stateMachine.transition(value, transition)
            .map { ProcessingResult.StateTransitioned(it) }
        }
      }
  }

  /**
   * Process a single message by ID.
   *
   * @param messageId The outbox message ID
   * @return Success with processing result, failure if not found or processing failed
   */
  fun processOne(messageId: String): Result<ProcessingResult<V>> {
    val message = outbox.findById(messageId)
      ?: return Result.failure(MessageNotFound(messageId))

    return processOne(message)
  }
}

/**
 * Error when a value cannot be found during effect processing.
 */
class ValueNotFound(val valueId: String) : Exception("Value not found: $valueId")

/**
 * Error when an outbox message cannot be found.
 */
class MessageNotFound(val messageId: String) : Exception("Message not found: $messageId")

/**
 * Storage interface for outbox messages.
 *
 * Implementations should use the same database as the value repository
 * to enable atomic writes during state transitions.
 *
 * @param ID The type of unique identifier for values
 * @param Ef The effect type
 */
interface Outbox<ID, Ef : Effect> {
  /**
   * Fetch pending messages up to the batch size.
   *
   * Messages with [OutboxMessage.dependsOnEffectId] set should only be returned
   * if the dependency has been processed (use [isProcessed] to check).
   *
   * @param batchSize Maximum number of messages to return
   * @param effectTypes If provided, only fetch messages with these effect types.
   *                    If null or empty, fetch all types.
   */
  fun fetchPending(batchSize: Int, effectTypes: Set<String>? = null): List<OutboxMessage<ID, Ef>>

  /**
   * Check if a message has been processed.
   *
   * Used to determine if dependent effects can be executed.
   *
   * @param id The message ID to check
   * @return true if the message has been processed, false otherwise
   */
  fun isProcessed(id: String): Boolean

  /**
   * Find a message by ID.
   */
  fun findById(id: String): OutboxMessage<ID, Ef>?

  /**
   * Mark a message as successfully processed.
   */
  fun markProcessed(id: String)

  /**
   * Mark a message as failed with an error.
   *
   * @param id The message ID
   * @param error The error message
   * @param maxAttempts If provided and the message has reached this many attempts,
   *                    it will be marked as DEAD_LETTER instead of FAILED.
   */
  fun markFailed(id: String, error: String, maxAttempts: Int? = null)

  /**
   * Mark a message as dead-lettered (will not be retried automatically).
   */
  fun markDeadLetter(id: String, error: String)

  /**
   * Fetch dead-lettered messages for manual handling or alerting.
   *
   * @param batchSize Maximum number of messages to return
   * @param effectTypes If provided, only fetch messages with these effect types
   */
  fun fetchDeadLetters(batchSize: Int = 100, effectTypes: Set<String>? = null): List<OutboxMessage<ID, Ef>>

  /**
   * Retry a dead-lettered message by resetting it to PENDING status.
   *
   * @param id The message ID
   * @return true if the message was found and reset, false otherwise
   */
  fun retryDeadLetter(id: String): Boolean
}

/**
 * Extension function to flatMap a Result.
 */
private fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
  fold(
    onSuccess = { transform(it) },
    onFailure = { Result.failure(it) }
  )
