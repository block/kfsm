package app.cash.kfsm

open class Transition<ID, V: Value<ID, V, S>, S : State<ID, V, S>>(val from: States<ID, V, S>, val to: S) {

  init {
    from.set.filterNot { state -> state.canDirectlyTransitionTo(to) }.let { invalidTransitions ->
      require(invalidTransitions.isEmpty()) { "invalid transition(s): ${invalidTransitions.map { fromState -> "$fromState->$to" }}" }
    }
  }

  constructor(from: S, to: S) : this(States(from), to)

  /** The effect executed when transitioning from [from] to [to]. */
  open fun effect(value: V): Result<V> = Result.success(value)

  /** The effect executed when transitioning from [from] to [to], but only when using `TransitionerAsync` */
  open suspend fun effectAsync(value: V): Result<V> = effect(value)
}
