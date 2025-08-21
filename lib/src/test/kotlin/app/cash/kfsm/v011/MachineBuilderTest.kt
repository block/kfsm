package app.cash.kfsm.v011

import app.cash.kfsm.A
import app.cash.kfsm.B
import app.cash.kfsm.C
import app.cash.kfsm.Char
import app.cash.kfsm.D
import app.cash.kfsm.E
import app.cash.kfsm.Letter
import app.cash.kfsm.Transition
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.String
import kotlin.runCatching

class MachineBuilderTest :
  StringSpec({
    "an empty machine" {
      fsm<String, Letter, Char> {}
        .getOrThrow()
        .transitionMap shouldBe emptyMap()
    }

    "a self loop" {
      fsm<String, Letter, Char> {
        B becomes {
          B via { it }
        }
      }.getOrThrow().transitionMap should {
        it.keys shouldContainOnly setOf(B)
        it[B]?.keys shouldContainOnly setOf(B)
      }
    }

    "mix of effect, transition and function values" {
      fsm<String, Letter, Char> {
        B becomes {
          B via { it }
          C via Effect { runCatching { it } }
          D via
            object : Transition<String, Letter, Char>(B, D) { }
        }
      }.getOrThrow().transitionMap should {
        it.keys shouldContainOnly setOf(B)
        it[B]?.keys shouldContainOnly setOf(B, C, D)
      }
    }

    "a full machine" {
      fsm<String, Letter, Char> {
        A.becomes {
          B.via { it }
        }
        B.becomes {
          B.via { it }
          C.via { it }
          D.via { it }
        }
        C.becomes {
          D.via { it }
        }
        D.becomes {
          B.via { it }
          E.via { it }
        }
      }.getOrThrow()
    }

    "disallows redeclaration of from state" {
      fsm<String, Letter, Char> {
        B.becomes {
          C.via { it }
        }
        B.becomes {
          D.via { it }
        }
      }.shouldBeFailure<IllegalStateException>().message shouldBe "State B has multiple `becomes` blocks defined"
    }

    "disallows redeclaration of to state" {
      fsm<String, Letter, Char> {
        B.becomes {
          C.via { it }
          C.via { it }
        }
      }.shouldBeFailure<IllegalStateException>().message shouldBe "State C already has a transition defined from B"
    }

    "disallows transitions between states that do not permit them" {
      fsm<String, Letter, Char> {
        C.becomes {
          B.via { it }
        }
      }.shouldBeFailure<IllegalStateException>().message shouldBe "State C declares that it cannot transition to B. Either the fsm declaration or the State is incorrect"
    }
  })
