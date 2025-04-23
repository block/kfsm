package app.cash.kfsm.exemplar

import app.cash.kfsm.States
import app.cash.kfsm.States.Companion.toStates
import app.cash.kfsm.Transition
import app.cash.kfsm.exemplar.Hamster.Asleep
import app.cash.kfsm.exemplar.Hamster.Awake
import app.cash.kfsm.exemplar.Hamster.Eating
import app.cash.kfsm.exemplar.Hamster.Resting
import app.cash.kfsm.exemplar.Hamster.RunningOnWheel

// Create your own base transition class in order to extend your transition collection with common functionality
abstract class HamsterTransition(
  from: States<Hamster.State>,
  to: Hamster.State
) : Transition<String, Hamster, Hamster.State>(from, to) {
  // Convenience constructor for when the from set has only one value
  constructor(from: Hamster.State, to: Hamster.State) : this(States(from), to)

  // Convenience constructor for the deprecated variant that takes a set instead of States
  constructor(from: Set<Hamster.State>, to: Hamster.State) : this(from.toStates(), to)

  // Demonstrates how you can add base behaviour to transitions for use in pre and post hooks.
  open val description: String = ""
}

class EatBreakfast(private val food: String) : HamsterTransition(from = Awake, to = Eating) {
  override fun effect(value: Hamster): Result<Hamster> =
    when (food) {
      "broccoli" -> {
        value.eat(food)
        Result.success(value)
      }

      "cheese" -> Result.failure(LactoseIntoleranceTroubles(food))
      else -> Result.success(value)
    }

  override val description = "eating $food for breakfast"
}

object RunOnWheel : HamsterTransition(from = Eating, to = RunningOnWheel) {
  override fun effect(value: Hamster): Result<Hamster> {
    // This println represents a side-effect
    println("$value moves to the wheel")
    return Result.success(value)
  }

  override val description = "running on the wheel"
}

object GoToBed : HamsterTransition(from = setOf(Eating, RunningOnWheel, Resting), to = Asleep) {
  override fun effect(value: Hamster): Result<Hamster> {
    value.sleep()
    return Result.success(value)
  }

  override val description = "going to bed"
}

object FlakeOut : HamsterTransition(from = setOf(Eating, RunningOnWheel), to = Resting) {
  override fun effect(value: Hamster): Result<Hamster> {
    println("$value has had enough and is sitting cute")
    return Result.success(value)
  }

  override val description = "tapping out"
}

object WakeUp : HamsterTransition(from = Asleep, to = Awake) {
  override fun effect(value: Hamster): Result<Hamster> {
    println("$value opens her eyes")
    return Result.success(value)
  }

  override val description = "waking up"
}

data class LactoseIntoleranceTroubles(val consumed: String) : Exception("Hamster tummy troubles eating $consumed")
