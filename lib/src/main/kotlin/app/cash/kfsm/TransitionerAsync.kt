package app.cash.kfsm

import app.cash.kfsm.annotations.ExperimentalLibraryApi

abstract class TransitionerAsync<ID, T : Transition<ID, V, S>, V : Value<ID, V, S>, S : State<ID, V, S>> {

  /**
   * Optional outbox handler for capturing deferrable effects.
   *
   * When set, transitions implementing [DeferrableEffect] will have their effects captured
   * and stored in the outbox instead of being executed immediately. This enables the
   * transactional outbox pattern where effects are persisted in the same transaction
   * as the state change.
   */
  @ExperimentalLibraryApi
  open val outboxHandler: OutboxHandler<ID, V, S>? = null

  open suspend fun preHook(value: V, via: T): Result<Unit> = Result.success(Unit)

  open suspend fun persist(from: S, value: V, via: T): Result<V> = Result.success(value)

  /**
   * Will be executed after the transition effect to persist the value along with outbox messages.
   *
   * Override this method when using the transactional outbox pattern to persist both the state
   * change and the captured effects in a single transaction.
   *
   * Example:
   * ```kotlin
   * override suspend fun persistWithOutbox(
   *     from: S,
   *     value: V,
   *     via: T,
   *     outboxMessages: List<OutboxMessage<ID>>
   * ): Result<V> = runCatching {
   *     database.transaction {
   *         database.update(value).getOrThrow()
   *         outboxMessages.forEach { database.insertOutbox(it) }
   *         value
   *     }
   * }
   * ```
   *
   * @param from The previous state
   * @param value The value with the new state
   * @param via The transition being applied
   * @param outboxMessages List of captured effects to persist in the same transaction
   * @return The persisted value
   */
  @ExperimentalLibraryApi
  open suspend fun persistWithOutbox(
    from: S,
    value: V,
    via: T,
    outboxMessages: List<OutboxMessage<ID>>
  ): Result<V> = persist(from, value, via)

  open suspend fun postHook(from: S, value: V, via: T): Result<Unit> = Result.success(Unit)

  suspend fun transition(
    value: V,
    transition: T
  ): Result<V> = when {
    transition.from.set.contains(value.state) -> doTheTransition(value, transition)
    // Self-cycled transitions will be effected by the first case.
    // If we still see a transition to self then this is a no-op.
    transition.to == value.state -> ignoreAlreadyCompletedTransition(value, transition)
    else -> Result.failure(InvalidStateForTransition(transition, value))
  }

  private suspend fun doTheTransition(
    value: V,
    transition: T
  ): Result<V> =
    runCatching { preHook(value, transition).getOrThrow() }
      .mapCatching { executeOrCaptureEffectAsync(value, transition).getOrThrow() }
      .map { it.update(transition.to) }
      .mapCatching { persistWithCapture(value.state, it, transition).getOrThrow() }
      .mapCatching { it.also { postHook(value.state, it, transition).getOrThrow() } }

  private suspend fun executeOrCaptureEffectAsync(value: V, transition: T): Result<V> {
    val handler = outboxHandler
    return if (handler != null && transition is DeferrableEffect<*, *, *>) {
      // Capture the effect for later execution
      @Suppress("UNCHECKED_CAST")
      handler.captureEffect(value, transition as DeferrableEffect<ID, V, S>)
    } else {
      // Execute immediately (current behavior)
      transition.effectAsync(value)
    }
  }

  private suspend fun persistWithCapture(from: S, value: V, transition: T): Result<V> {
    val handler = outboxHandler
    return if (handler != null) {
      val messages = handler.getPendingMessages()
      persistWithOutbox(from, value, transition, messages)
        .also { handler.clearPending() }
    } else {
      persist(from, value, transition)
    }
  }

  private fun ignoreAlreadyCompletedTransition(
    value: V,
    transition: T
  ): Result<V> = Result.success(value.update(transition.to))
}

