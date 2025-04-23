package app.cash.kfsm.exemplar

import app.cash.kfsm.Value

data class Hamster(
  val name: String,
  override val state: State,
): Value<String, Hamster, Hamster.State> {
  override fun update(newState: State): Hamster = this.copy(state = newState)

  override val id : String = name

  fun eat(food: String) {
    println("@ (･ｪ･´)◞    (eats $food)")
  }

  fun sleep() {
    println("◟(`･ｪ･)  ╥━╥   (goes to bed)")
  }

  sealed class State(to: () -> Set<State>) : app.cash.kfsm.State<State>(to)

  /** Hamster is awake... and hungry! */
  data object Awake : State({ setOf(Eating) })

  /** Hamster is eating ... what will they do next? */
  data object Eating : State({ setOf(RunningOnWheel, Asleep, Resting) })

  /** Wheeeeeee! */
  data object RunningOnWheel : State({ setOf(Asleep, Resting) })

  /** Sits in the corner, chilling */
  data object Resting : State({ setOf(Asleep) })

  /** Zzzzzzzzz */
  data object Asleep : State({ setOf(Awake) })
}
