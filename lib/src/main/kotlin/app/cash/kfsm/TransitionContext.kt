package app.cash.kfsm

/**
 * Holds the context for a state transition, including the value being transformed and the states involved.
 *
 * This class provides access to both the value being transformed and the states involved in the transition,
 * allowing effects to make decisions based on the full transition context rather than just the value.
 *
 * @param ID The type of identifier used in the state machine
 * @param V The type of value being transformed
 * @param S The type of state in the state machine
 * @property value The value being transformed during the transition
 * @property from The source state of the transition
 * @property to The target state of the transition
 */
data class TransitionContext<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
  val value: V,
  val from: S,
  val to: S
)
