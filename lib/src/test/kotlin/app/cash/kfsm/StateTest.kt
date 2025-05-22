package app.cash.kfsm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class StateTest : StringSpec({

  "state knows which states it can directly transition to" {
    A.subsequentStates shouldBe setOf(B)
    B.subsequentStates shouldBe setOf(B, C, D)
    C.subsequentStates shouldBe setOf(D)
    D.subsequentStates shouldBe setOf(B, E)
    E.subsequentStates shouldBe emptySet()
  }

  "state knows which states it can eventually transition to" {
    A.reachableStates shouldBe setOf(B, C, D, E)
    B.reachableStates shouldBe setOf(B, C, D, E)
    C.reachableStates shouldBe setOf(B, C, D, E)
    D.reachableStates shouldBe setOf(B, C, D, E)
    E.reachableStates shouldBe emptySet()
  }

  "state reports that it can transition to another state" {
    A.canDirectlyTransitionTo(B) shouldBe true
    B.canDirectlyTransitionTo(B) shouldBe true // self
  }

  "state reports that it cannot transition to another state" {
    A.canDirectlyTransitionTo(A) shouldBe false
    A.canDirectlyTransitionTo(C) shouldBe false
    A.canDirectlyTransitionTo(D) shouldBe false
    A.canDirectlyTransitionTo(E) shouldBe false

    C.canDirectlyTransitionTo(B) shouldBe false // reverse
    C.canDirectlyTransitionTo(C) shouldBe false // self
  }

  "state reports if it can eventually transition to another state" {
    A.canEventuallyTransitionTo(B) shouldBe true
    A.canEventuallyTransitionTo(C) shouldBe true
    A.canEventuallyTransitionTo(D) shouldBe true
    A.canEventuallyTransitionTo(E) shouldBe true
  }

  "state reports if it can eventually transition to another state via a cycle" {
    C.canEventuallyTransitionTo(B) shouldBe true
    C.canEventuallyTransitionTo(C) shouldBe true
  }

  "state reports if it cannot eventually transition to another state" {
    A.canEventuallyTransitionTo(A) shouldBe false
    C.canEventuallyTransitionTo(A) shouldBe false
    E.canEventuallyTransitionTo(C) shouldBe false
    E.canEventuallyTransitionTo(E) shouldBe false
  }

  "can define and use custom methods on a state" {
    A.next(2) shouldBe listOf(B, C)
    E.next(2) shouldBe emptyList()
  }

  "returns shortest path" {
    Red.shortestPathTo(Green) shouldBe listOf(Red, Yellow, Green)
    Red.shortestPathTo(Yellow) shouldBe listOf(Red, Yellow)
    Green.shortestPathTo(Red) shouldBe listOf(Green, Yellow, Red)
  }
})
