package app.cash.kfsm

interface StateMachine<ID, V : Value<ID, V, S>, S : State<ID, V, S>> {
  fun getAvailableTransitions(state: S): kotlin.collections.Set<Transition<ID, V, S>>
  fun <T : Transition<ID, V, S>> getTransition(): T
  fun execute(value: V, transition: Transition<ID, V, S>): Result<V>
  fun transitionToState(value: V, targetState: S): Result<V>
}
