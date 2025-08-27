package app.cash.kfsm

/**
 * Base class for transitions between states in a state machine.
 *
 * A transition represents a path from one state to another, along with an effect that transforms
 * the value during that transition. The transition validates that the source states can legally
 * transition to the target state.
 *
 * @param ID The type of identifier used in the state machine
 * @param V The type of value being transformed
 * @param S The type of state in the state machine
 * @property from The set of valid source states for this transition
 * @property to The target state for this transition
 */
open class Transition<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
  val from: States<ID, V, S>,
  val to: S
) : Effect<ID, V, S> {
  init {
    from.set.filterNot { state -> state.canDirectlyTransitionTo(to) }.let { invalidTransitions ->
      require(invalidTransitions.isEmpty()) {
        "invalid transition(s): ${invalidTransitions.map { fromState ->
          "$fromState->$to"
        }}"
      }
    }
  }

  /**
   * Creates a transition from a single source state to a target state.
   *
   * @param from The source state
   * @param to The target state
   */
  constructor(from: S, to: S) : this(States.of(from), to)

  /** The effect executed when transitioning from [from] to [to]. */
  open fun effect(value: V): Result<V> = Result.success(value)

  override fun apply(context: TransitionContext<ID, V, S>): Result<V> = effect(context.value)

  /** The effect executed when transitioning from [from] to [to], but only when using `TransitionerAsync` */
  open suspend fun effectAsync(value: V): Result<V> = effect(value)
}
