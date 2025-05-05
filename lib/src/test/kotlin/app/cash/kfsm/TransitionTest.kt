package app.cash.kfsm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec

class TransitionTest : StringSpec({

  "cannot create an invalid state transition" {
    shouldThrow<IllegalArgumentException> { LetterTransition(A, C) }
  }

  "cannot create an invalid state transition from a set of states" {
    shouldThrow<IllegalArgumentException> { LetterTransition(States(B, A), C) }
  }

})

open class LetterTransition(from: States<String, Letter, app.cash.kfsm.Char>, to: app.cash.kfsm.Char): Transition<String, Letter, app.cash.kfsm.Char>(from, to) {
  constructor(from: app.cash.kfsm.Char, to: app.cash.kfsm.Char) : this(States(from), to)

  val specificToThisTransitionType: String = "${from.set} -> $to"
}

