package app.cash.kfsm

abstract class Transitioner<ID, T : Transition<ID, V, S>, V : Value<ID, V, S>, S : State<ID, V, S>> {

  /** Will be executed prior to the transition effect. Failure here will terminate the transition */
  open fun preHook(value: V, via: T): Result<Unit> = Result.success(Unit)

  /** Will be executed after the transition effect. Use this to persist the value. */
  open fun persist(from: S, value: V, via: T): Result<V> = Result.success(value)

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
      .mapCatching { transition.effect(value).getOrThrow() }
      .mapCatching { it.validateAndUpdate(transition.to).getOrThrow() }
      .mapCatching { persist(value.state, it, transition).getOrThrow() }
      .mapCatching { it.also { postHook(value.state, it, transition).getOrThrow() } }

  private fun ignoreAlreadyCompletedTransition(
    value: V,
    transition: T
  ): Result<V> = Result.success(value.update(transition.to))
}

