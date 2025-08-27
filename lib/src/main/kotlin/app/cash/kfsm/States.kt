package app.cash.kfsm

/** A collection of states that is guaranteed to be non-empty. */
data class States<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(val a: S, val other: Set<S>) {
  constructor(first: S, vararg others: S) : this(first, others.toSet())

  val set: Set<S> = other + a

  companion object {
    fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> of(state: S): States<ID, V, S> = States(state, emptySet())

    fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> Set<S>.toStates(): States<ID, V, S> = when {
      isEmpty() -> throw IllegalArgumentException("Cannot create States from empty set")
      else -> toList().let { States(it.first(), it.drop(1).toSet()) }
    }
  }
}
