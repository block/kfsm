package app.cash.kfsm

abstract class TransitionerAsync<T : Transition<V, S>, V : Value<V, S>, S : State<S>> {

  open suspend fun preHook(value: V, via: T): Result<Unit> = Result.success(Unit)

  open suspend fun persist(value: V, via: T): Result<V> = Result.success(value)

  open suspend fun postHook(from: S, value: V, via: T): Result<Unit> = Result.success(Unit)

  suspend fun transition(
    value: V,
    transition: T
  ): Result<V> = when {
    transition.from.set.contains(value.state) -> doTheTransition(value, transition)
    // Self-cycled transitions will be effected by the first case.
    // If we still see a transition to self then this is a no-op.
    transition.to == value.state -> ignoreAlreadyCompletedTransition(value, transition)
    else -> Result.failure(InvalidStateTransition(transition, value))
  }

  private suspend fun doTheTransition(
    value: V,
    transition: T
  ): Result<V> =
    runCatching { preHook(value, transition).getOrThrow() }
      .mapCatching { transition.effectAsync(value).getOrThrow() }
      .map { it.update(transition.to) }
      .mapCatching { persist(it, transition).getOrThrow() }
      .mapCatching { it.also { postHook(value.state, it, transition).getOrThrow() } }

  private fun ignoreAlreadyCompletedTransition(
    value: V,
    transition: T
  ): Result<V> = Result.success(value.update(transition.to))
}

