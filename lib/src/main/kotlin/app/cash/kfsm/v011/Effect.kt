package app.cash.kfsm.v011

import app.cash.kfsm.State
import app.cash.kfsm.Value

fun interface Effect<ID, V : Value<ID, V, S>, S : State<ID, V, S>> {
  fun apply(v: V): Result<V>
}
