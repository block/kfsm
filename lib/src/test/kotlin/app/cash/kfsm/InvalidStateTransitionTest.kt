package app.cash.kfsm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InvalidStateTransitionTest : StringSpec({
  "with single from-state has correct message" {
    InvalidStateForTransition(LetterTransition(A, B), Letter(E, id = "my_letter")).message shouldBe
      "Value cannot transition {A} to B, because it is currently E. [id=my_letter]"
  }

  "with many from-states has correct message" {
    InvalidStateForTransition(LetterTransition(States(C, B), D), Letter(E, "my_letter")).message shouldBe
      "Value cannot transition {B, C} to D, because it is currently E. [id=my_letter]"
  }

  "exposes transition and state" {
    val error = InvalidStateForTransition(LetterTransition(A, B), Letter(E, "my_letter"))

    error.getState<Char>() shouldBe E
  }
})
