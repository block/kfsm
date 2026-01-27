package app.cash.kfsm

/**
 * A Decision represents either:
 * - A successful transition to a new state with optional effects to emit
 * - A rejection of the event (the event is not valid for the current state)
 *
 * The separation of decision-making (pure) from effect execution (impure) enables:
 * - Easy testing of business logic without side effects
 * - Transactional outbox pattern for reliable effect processing
 * - Clear audit trail of state changes and their causes
 *
 * @param S The state type
 * @param E The effect type
 */
sealed class Decision<S : State<S>, E : Effect> {
  /**
   * The event was accepted and resulted in a state transition.
   *
   * @param state The new state after the transition
   * @param effects Effects to be stored in the outbox and processed after commit
   */
  data class Accept<S : State<S>, E : Effect>(
    val state: S,
    val effects: List<E> = emptyList()
  ) : Decision<S, E>() {
    constructor(state: S, vararg effects: E) : this(state, effects.toList())
  }

  /**
   * The event was rejected and no state change occurred.
   *
   * @param reason A description of why the event was rejected
   */
  data class Reject<S : State<S>, E : Effect>(val reason: String) : Decision<S, E>()

  companion object {
    fun <S : State<S>, E : Effect> accept(state: S, vararg effects: E): Decision<S, E> =
      Accept(state, effects.toList())

    fun <S : State<S>, E : Effect> accept(state: S, effects: List<E> = emptyList()): Decision<S, E> =
      Accept(state, effects)

    fun <S : State<S>, E : Effect> reject(reason: String): Decision<S, E> = Reject(reason)
  }
}
