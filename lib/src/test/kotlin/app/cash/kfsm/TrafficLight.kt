package app.cash.kfsm

/**
 * A simple state machine modeling a traffic light.
 */
data class TrafficLight(override val state: Colour, override val id: String) : Value<String, TrafficLight, Colour> {
  override fun update(newState: Colour): TrafficLight = copy(state = newState)
}

sealed class Colour(to: () -> Set<Colour>) : State<String, TrafficLight, Colour>(to) {
  fun next(count: Int): List<Colour> =
    if (count <= 0) emptyList()
    else subsequentStates.filterNot { it == this }.firstOrNull()?.let { listOf(it) + it.next(count - 1) } ?: emptyList()
}

data object Red : Colour(to = { setOf(Yellow) })
data object Yellow : Colour(to = { setOf(Green, Red) })
data object Green : Colour(to = { setOf(Yellow) })
