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
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.String
import kotlin.runCatching

class MachineBuilderTest :
  StringSpec({
    "an empty machine" {
      fsm<String, Letter, Char> {}
        .transitionMap shouldBe emptyMap()
    }

    "a self loop" {
      fsm<String, Letter, Char> {
        B becomes {
          B via { it }
        }
      }.transitionMap should {
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
      }.transitionMap should {
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
      }
    }
  })
