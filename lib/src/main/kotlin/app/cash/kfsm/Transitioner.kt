package app.cash.kfsm

abstract class Transitioner<ID, T : Transition<ID, V, S>, V : Value<ID, V, S>, S : State<ID, V, S>> {

  /**
   * Optional outbox handler for capturing deferrable effects.
   *
   * When set, transitions implementing [DeferrableEffect] will have their effects captured
   * and stored in the outbox instead of being executed immediately. This enables the
   * transactional outbox pattern where effects are persisted in the same transaction
   * as the state change.
   */
  open val outboxHandler: OutboxHandler<ID, V, S>? = null

  /** Will be executed prior to the transition effect. Failure here will terminate the transition */
  open fun preHook(value: V, via: T): Result<Unit> = Result.success(Unit)

  /** Will be executed after the transition effect. Use this to persist the value. */
  open fun persist(from: S, value: V, via: T): Result<V> = Result.success(value)

  /**
   * Will be executed after the transition effect to persist the value along with outbox messages.
   *
   * Override this method when using the transactional outbox pattern to persist both the state
   * change and the captured effects in a single transaction.
   *
   * Example:
   * ```kotlin
   * override fun persistWithOutbox(
   *     from: S,
   *     value: V,
   *     via: T,
   *     outboxMessages: List<OutboxMessage<ID>>
   * ): Result<V> = runCatching {
   *     database.runInTransaction {
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
  open fun persistWithOutbox(
    from: S,
    value: V,
    via: T,
    outboxMessages: List<OutboxMessage<ID>>
  ): Result<V> = persist(from, value, via)

  /** Will be executed after the transition effect & value persistence. Use this to perform side effects such as notifications. */
  open fun postHook(from: S, value: V, via: T): Result<Unit> = Result.success(Unit)

  /**
   * Execute the given transition on the given value.
   *
   * If the target state is already present, then this is a no-op.
   * If the provided transition cannot apply to the value's state, then this is a failure.
   * Otherwise, the transition is applied and the state is updated in the returned value.
   */
  fun transition(
    value: V,
    transition: T
  ): Result<V> = when {
    transition.from.set.contains(value.state) -> doTheTransition(value, transition)
    // Self-cycled transitions will be effected by the first case.
    // If we still see a transition to self then this is a no-op.
    transition.to == value.state -> ignoreAlreadyCompletedTransition(value, transition)
    else -> Result.failure(InvalidStateForTransition(transition, value))
  }

  private fun <ID, V: Value<ID, V, S>, S : State<ID, V, S>> Value<ID, V, S>
    .validateAndUpdate(newState: S): Result<V> = newState.validate(update(newState))

  private fun doTheTransition(
    value: V,
    transition: T
  ): Result<V> =
    runCatching { preHook(value, transition).getOrThrow() }
      .mapCatching { executeOrCaptureEffect(value, transition).getOrThrow() }
      .mapCatching { it.validateAndUpdate(transition.to).getOrThrow() }
      .mapCatching { persistWithCapture(value.state, it, transition).getOrThrow() }
      .mapCatching { it.also { postHook(value.state, it, transition).getOrThrow() } }

  private fun executeOrCaptureEffect(value: V, transition: T): Result<V> {
    val handler = outboxHandler
    return if (handler != null && transition is DeferrableEffect<*, *, *>) {
      // Capture the effect for later execution
      @Suppress("UNCHECKED_CAST")
      handler.captureEffect(value, transition as DeferrableEffect<ID, V, S>)
    } else {
      // Execute immediately (current behavior)
      transition.effect(value)
    }
  }

  private fun persistWithCapture(from: S, value: V, transition: T): Result<V> {
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

