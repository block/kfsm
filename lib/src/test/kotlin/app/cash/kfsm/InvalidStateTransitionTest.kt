package app.cash.kfsm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InvalidStateTransitionTest : StringSpec({
  "with single from-state has correct message" {
    InvalidStateTransition(LetterTransition(A, B), Letter(E)).message shouldBe
      "Value cannot transition {A} to B, because it is currently E. [value=Letter(state=E)]"
  }

  "with many from-states has correct message" {
    InvalidStateTransition(LetterTransition(States(C, B), D), Letter(E)).message shouldBe
      "Value cannot transition {B, C} to D, because it is currently E. [value=Letter(state=E)]"
  }
})
