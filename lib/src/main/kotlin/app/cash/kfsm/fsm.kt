package app.cash.kfsm

/**
 * Creates a new state machine using a builder DSL.
 *
 * @param ID The type of the identifier used in the state machine
 * @param V The type of value being transformed
 * @param S The type of state in the state machine
 * @param factory A function that creates new value instances given an ID and state
 * @param transitioner The transitioner to use for state transitions
 * @param block A builder block that defines the state machine's transitions
 * @return A new [StateMachine] instance
 */
inline fun <reified ID, V : Value<ID, V, S>, S : State<ID, V, S>> fsm(
  factory: (ID, S) -> V,
  transitioner: Transitioner<ID, Transition<ID, V, S>, V, S>,
  noinline block: MachineBuilder<ID, V, S>.() -> Unit
): Result<StateMachine<ID, V, S>> = runCatching { MachineBuilder<ID, V, S>().apply(block).build(transitioner) }
