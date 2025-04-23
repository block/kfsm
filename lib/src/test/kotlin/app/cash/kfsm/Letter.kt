package app.cash.kfsm

/** A simple state machine that represents a letter of the alphabet. */
data class Letter(override val state: Char, override val id: String) : Value<String, Letter, Char> {
  override fun update(newState: Char): Letter = copy(state = newState)
}

sealed class Char(to: () -> Set<Char>) : State<Char>(to) {
  fun next(count: Int): List<Char> =
    if (count <= 0) emptyList()
    else subsequentStates.filterNot { it == this }.firstOrNull()?.let { listOf(it) + it.next(count - 1) } ?: emptyList()
}

data object A : Char(to = { setOf(B) })
data object B : Char(to = { setOf(B, C, D) })
data object C : Char(to = { setOf(D) })
data object D : Char(to = { setOf(B, E) })
data object E : Char(to = { emptySet() })
