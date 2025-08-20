package app.cash.kfsm.v011

import app.cash.kfsm.State
import app.cash.kfsm.Value

class StateMachine<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
  val transitionMap: Map<S, Map<S, Effect<ID, V, S>>>
)
