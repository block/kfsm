package app.cash.kfsm.v2

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A wrapper that provides suspending transition with timeout over the async outbox-based state machine.
 *
 * This implementation uses database polling to support multi-instance deployments.
 * When a caller applies a transition and waits, a pending request is stored in the database.
 * Any instance processing the workflow can mark it complete, and the waiting caller will
 * see the result on its next poll.
 *
 * Flow:
 * 1. Caller calls `transitionAndAwait(value, transition, timeout)`
 * 2. A pending request is stored in the database
 * 3. Transition is applied and effects stored in outbox
 * 4. Caller polls the database for completion
 * 5. EffectProcessor (on any instance) processes effects
 * 6. When a settled state is reached, `markCompleted` is called
 * 7. Polling caller sees completion and returns
 *
 * A "settled" state is one where automatic effect processing cannot make further progress:
 * - Terminal states (completed, failed, cancelled)
 * - States awaiting external input (user action, webhook, manual approval)
 *
 * Example:
 * ```kotlin
 * val store = PendingRequestStore(database)
 * val awaitable = AwaitableStateMachine(stateMachine, store) { state ->
 *   // Settled states: terminal OR waiting for external input
 *   state is OrderState.Completed ||
 *   state is OrderState.Cancelled ||
 *   state is OrderState.AwaitingUserVerification
 * }
 *
 * // In EffectProcessor, call markCompleted on settled states
 * // awaitable.markCompleted(valueId, finalValue)
 *
 * // Caller suspends until settled state or timeout
 * val result = awaitable.transitionAndAwait(
 *   value = withdrawal,
 *   transition = StartWithdrawal(amount),
 *   timeout = 30.seconds
 * )
 * ```
 *
 * @param ID The type of unique identifier for values
 * @param V The value type
 * @param S The state type
 * @param Ef The effect type
 * @param isSettled Predicate to determine if the await should resolve. Returns true when:
 *   - The workflow has reached a terminal state (completed, failed, cancelled)
 *   - The workflow is waiting for external input (user action, webhook, manual approval)
 *   - Any state where automatic effect processing cannot make further progress
 */
class AwaitableStateMachine<ID, V : Value<ID, V, S>, S : State<S>, Ef : Effect>(
  private val stateMachine: StateMachine<ID, V, S, Ef>,
  private val pendingRequestStore: PendingRequestStore<ID, V>,
  private val isSettled: (S) -> Boolean,
  private val pollInterval: Duration = 100.milliseconds
) {
  /**
   * Secondary constructor for convenience when settled states are simple objects (not parameterized).
   */
  constructor(
    stateMachine: StateMachine<ID, V, S, Ef>,
    pendingRequestStore: PendingRequestStore<ID, V>,
    settledStates: Set<S>,
    pollInterval: Duration = 100.milliseconds
  ) : this(stateMachine, pendingRequestStore, { state -> settledStates.contains(state) }, pollInterval)
  /**
   * Apply a transition and suspend until the workflow reaches a settled state.
   *
   * A settled state is one where automatic effect processing cannot make further progress,
   * including terminal states (completed, failed) and states awaiting external input.
   *
   * @param value The initial value
   * @param transition The transition to apply
   * @param timeout Maximum time to wait for settlement
   * @return The final value when a settled state is reached
   * @throws kotlinx.coroutines.TimeoutCancellationException if the timeout expires before settlement
   * @throws RejectedTransition if the transition is rejected
   * @throws Exception if the workflow fails
   */
  suspend fun transitionAndAwait(value: V, transition: Transition<ID, V, S, Ef>, timeout: Duration): Result<V> {
    val requestId = pendingRequestStore.create(value.id)

    return try {
      // Apply the initial transition (persists to outbox)
      val initialResult = stateMachine.transition(value, transition)

      if (initialResult.isFailure) {
        pendingRequestStore.delete(requestId)
        return initialResult
      }

      val updatedValue = initialResult.getOrThrow()

      // If already settled, return immediately
      if (isSettled(updatedValue.state)) {
        pendingRequestStore.delete(requestId)
        return Result.success(updatedValue)
      }

      // Poll for completion
      val finalValue = withTimeout(timeout) {
        pollForCompletion(requestId)
      }
      Result.success(finalValue)
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
      pendingRequestStore.markTimedOut(requestId)
      Result.failure(WorkflowTimeoutException(value.id, timeout))
    } catch (e: WorkflowFailedException) {
      Result.failure(e)
    } catch (e: Exception) {
      pendingRequestStore.delete(requestId)
      Result.failure(e)
    }
  }

  private suspend fun pollForCompletion(requestId: String): V {
    while (true) {
      when (val status = pendingRequestStore.getStatus(requestId)) {
        is PendingRequestStatus.Waiting -> delay(pollInterval)
        is PendingRequestStatus.Completed -> {
          pendingRequestStore.delete(requestId)
          return status.value
        }
        is PendingRequestStatus.Failed -> {
          pendingRequestStore.delete(requestId)
          throw WorkflowFailedException(status.error)
        }
        is PendingRequestStatus.NotFound -> {
          throw IllegalStateException("Pending request $requestId not found")
        }
      }
    }
  }

  /**
   * Mark a workflow as completed with the final value.
   *
   * Call this from [EffectProcessor] when a settled state is reached.
   * This can be called from any instance - it updates the database so
   * the polling caller (on any instance) will see the result.
   *
   * @param valueId The ID of the value
   * @param value The final value state
   */
  fun markCompleted(valueId: ID, value: V) {
    if (isSettled(value.state)) {
      pendingRequestStore.complete(valueId, value)
    }
  }

  /**
   * Mark a workflow as failed.
   *
   * Call this from [EffectProcessor] when effect processing fails.
   *
   * @param valueId The ID of the value
   * @param error The error that occurred
   */
  fun markFailed(valueId: ID, error: Throwable) {
    pendingRequestStore.fail(valueId, error.message ?: "Unknown error")
  }
}

/**
 * Storage for pending requests that support multi-instance deployments.
 *
 * Implementations should use a shared database (or similar distributed storage)
 * so that any instance can mark a request complete and any instance can poll for results.
 *
 * @param ID The type of unique identifier for values
 * @param V The value type
 */
interface PendingRequestStore<ID, V> {
  /**
   * Create a new pending request.
   *
   * @param valueId The ID of the value being processed
   * @return A unique request ID
   */
  fun create(valueId: ID): String

  /**
   * Get the current status of a pending request.
   *
   * @param requestId The request ID returned from [create]
   * @return The current status
   */
  fun getStatus(requestId: String): PendingRequestStatus<V>

  /**
   * Mark a request as completed with the final value.
   *
   * Note: This looks up requests by valueId (not requestId) so that
   * any instance can mark completion without knowing the request ID.
   *
   * @param valueId The value ID
   * @param value The final value
   */
  fun complete(valueId: ID, value: V)

  /**
   * Mark a request as failed.
   *
   * @param valueId The value ID
   * @param error The error message
   */
  fun fail(valueId: ID, error: String)

  /**
   * Mark a request as timed out.
   *
   * @param requestId The request ID
   */
  fun markTimedOut(requestId: String)

  /**
   * Delete a pending request (cleanup).
   *
   * @param requestId The request ID
   */
  fun delete(requestId: String)
}

/**
 * Status of a pending request.
 */
sealed class PendingRequestStatus<out V> {
  data object Waiting : PendingRequestStatus<Nothing>()
  data class Completed<V>(val value: V) : PendingRequestStatus<V>()
  data class Failed(val error: String) : PendingRequestStatus<Nothing>()
  data object NotFound : PendingRequestStatus<Nothing>()
}

/**
 * Exception thrown when a workflow times out waiting for completion.
 */
class WorkflowTimeoutException(
  val valueId: Any?,
  val timeout: Duration
) : Exception("Workflow for value $valueId timed out after $timeout")

/**
 * Exception thrown when a workflow fails during processing.
 */
class WorkflowFailedException(
  val error: String
) : Exception("Workflow failed: $error")
