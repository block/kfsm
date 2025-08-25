package app.cash.kfsm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
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
      val machine =
        fsm<String, Letter, Char> {
          A.becomes {
            B.via { it }
          }
          B.becomes {
            B.via { it }
            C.via { it }
            D.via { it.copy(id = "dave") }
          }
          C.becomes {
            D.via { it }
          }
          D.becomes {
            B.via { it }
            E.via { it }
          }
        }.getOrThrow()

      machine.transitionTo(Letter(B, "barry"), D).getOrThrow() shouldBe Letter(D, "dave")
    }

    "disallows becomes block with no targets" {
      fsm<String, Letter, Char> {
        B.becomes {}
      }.shouldBeFailure<IllegalStateException>().message shouldBe
        "State B defines a `becomes` block with no transitions"
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
      }.shouldBeFailure<IllegalStateException>().message shouldBe "State C declares that it cannot transition to B. " +
        "Either the fsm declaration or the State is incorrect"
    }

    "can optionally define controllers" {
      val machine =
        fsm<String, Letter, Char> {
          A.becomes {
            B.via { it.copy(id = "bedford") }
          }
          B.becomes(selector = { Result.success(C) }) {
            B.via { it }
            C.via { it.copy(id = "citroën") }
            D.via { it }
          }
          C.becomes {
            D.via { it.copy(id = "datsun") }
          }
          D.becomes {
            B.via { it.copy(id = "bristol") }
            E.via { it }
          }
        }.getOrThrow()

      val a = Letter(A, "audi")
      val b = Letter(B, "bedford")
      val c = Letter(C, "citroën")
      val d = Letter(D, "datsun")
      val e = Letter(E, "eagle")

      machine.advance(a).shouldBeSuccess(b)
      machine.advance(b).shouldBeSuccess(c)
      machine.advance(c).shouldBeSuccess(d)
      machine.advance(d).shouldBeFailure<IllegalStateException>().message shouldBe
        "State D has multiple subsequent states, but no NextStateSelector was provided"
      machine.transitionTo(d, B).shouldBeSuccess(b.copy(id = "bristol"))
      machine.advance(e).shouldBeFailure<IllegalStateException>().message shouldBe "No selector for state E"
    }
  })
