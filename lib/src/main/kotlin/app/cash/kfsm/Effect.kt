package app.cash.kfsm

fun interface Effect<ID, V : Value<ID, V, S>, S : State<ID, V, S>> {
  fun apply(v: V): Result<V>
}
