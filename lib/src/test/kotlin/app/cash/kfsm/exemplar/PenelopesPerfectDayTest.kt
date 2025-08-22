package app.cash.kfsm.exemplar

import app.cash.kfsm.StateMachineUtilities
import app.cash.kfsm.exemplar.Hamster.Asleep
import app.cash.kfsm.exemplar.Hamster.Awake
import app.cash.kfsm.exemplar.Hamster.Eating
import app.cash.kfsm.exemplar.Hamster.RunningOnWheel
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

class PenelopesPerfectDayTest :
  StringSpec({
    isolationMode = IsolationMode.InstancePerTest

    val hamster = Hamster(name = "Penelope", state = Awake)

    // In this example we extend the transitioner with our own type `HamsterTransitioner` in order to define
    // hooks that will be executed before each transition and after each successful transition.
    val transitioner = HamsterTransitioner()

    "a newly woken hamster eats broccoli" {
      val result = transitioner.transition(hamster, EatBreakfast("broccoli")).shouldBeSuccess()
      result.state shouldBe Eating
      transitioner.locks shouldBe listOf(hamster)
      transitioner.unlocks shouldBe listOf(result)
      transitioner.saves shouldBe listOf(result)
      transitioner.notifications shouldBe
        listOf("Penelope was Awake, then began eating broccoli for breakfast and is now Eating")
    }

    "the hamster has trouble eating cheese" {
      transitioner.transition(hamster, EatBreakfast("cheese")) shouldBeFailure
        LactoseIntoleranceTroubles("cheese")
      transitioner.locks shouldBe listOf(hamster)
      transitioner.unlocks.shouldBeEmpty()
      transitioner.saves.shouldBeEmpty()
      transitioner.notifications.shouldBeEmpty()
    }

    "a sleeping hamster can awaken yet again" {
      transitioner
        .transition(hamster, EatBreakfast("broccoli"))
        .map { transitioner.transition(it, RunOnWheel).getOrThrow() }
        .map { transitioner.transition(it, GoToBed).getOrThrow() }
        .map { transitioner.transition(it, WakeUp).getOrThrow() }
        .map { transitioner.transition(it, EatBreakfast("broccoli")).getOrThrow() }
        .shouldBeSuccess()
        .state shouldBe Eating
      transitioner.locks shouldBe
        listOf(
          hamster,
          hamster.copy(state = Eating),
          hamster.copy(state = RunningOnWheel),
          hamster.copy(state = Asleep),
          hamster.copy(state = Awake)
        )
      transitioner.unlocks shouldBe transitioner.saves
      transitioner.saves shouldBe
        listOf(
          hamster.copy(state = Eating),
          hamster.copy(state = RunningOnWheel),
          hamster.copy(state = Asleep),
          hamster.copy(state = Awake),
          hamster.copy(state = Eating)
        )
      transitioner.notifications shouldBe
        listOf(
          "Penelope was Awake, then began eating broccoli for breakfast and is now Eating",
          "Penelope was Eating, then began running on the wheel and is now RunningOnWheel",
          "Penelope was RunningOnWheel, then began going to bed and is now Asleep",
          "Penelope was Asleep, then began waking up and is now Awake",
          "Penelope was Awake, then began eating broccoli for breakfast and is now Eating"
        )
    }

    "a sleeping hamster cannot immediately start running on the wheel" {
      transitioner.transition(hamster.copy(state = Asleep), RunOnWheel).shouldBeFailure()
      transitioner.locks.shouldBeEmpty()
      transitioner.unlocks.shouldBeEmpty()
      transitioner.saves.shouldBeEmpty()
      transitioner.notifications.shouldBeEmpty()
    }

    "an eating hamster who wants to eat twice as hard will just keep eating" {
      val eatingHamster = hamster.copy(state = Eating)
      transitioner
        .transition(eatingHamster, EatBreakfast("broccoli"))
        .shouldBeSuccess(eatingHamster)
      transitioner.locks.shouldBeEmpty()
      transitioner.unlocks.shouldBeEmpty()
      transitioner.saves.shouldBeEmpty()
      transitioner.notifications.shouldBeEmpty()
    }

    // Add a test like this to ensure you don't have states that cannot be reached
    "the state machine is hunky dory" {
      StateMachineUtilities.verify(Awake).shouldBeSuccess()
    }

    // Use this method to create mermaid diagrams in your markdown.
    // TODO(jem) - add a custom kotest matcher for ensuring the markdown is in a specific project file.
    "the mermaid diagram should be correct" {
      StateMachineUtilities.mermaid(Awake).shouldBeSuccess(
        """
        stateDiagram-v2
        [*] --> Awake
        Asleep --> Awake
        Awake --> Eating
        Eating --> Asleep
        Eating --> Resting
        Eating --> RunningOnWheel
        Resting --> Asleep
        RunningOnWheel --> Asleep
        RunningOnWheel --> Resting
        """.trimIndent()
      )
    }
  })
