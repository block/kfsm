package app.cash.kfsm

import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MachineBuilderTest {
  @Test
  fun `allows transitions between states that permit them`() {
    fsm<String, Letter, Char> {
      B.becomes {
        C.via { it.value }
      }
    }.getOrThrow().transitionMap should {
      it.keys shouldContainOnly setOf(B)
      it[B]?.keys shouldContainOnly setOf(C)
    }
  }

  @Test
  fun `allows multiple transitions from a state`() {
    fsm<String, Letter, Char> {
      B.becomes {
        B.via { it.value }
        C.via { it.value }
        D.via { it.value }
      }
    }.getOrThrow().transitionMap should {
      it.keys shouldContainOnly setOf(B)
      it[B]?.keys shouldContainOnly setOf(B, C, D)
    }
  }

  @Test
  fun `a full machine`() {
    val machine =
      fsm<String, Letter, Char> {
        A.becomes {
          B.via { it.value }
        }
        B.becomes {
          B.via { it.value }
          C.via { it.value }
          D.via { it.value.copy(id = "dave") }
        }
        C.becomes {
          D.via { it.value }
        }
        D.becomes {
          B.via { it.value }
          E.via { it.value }
        }
      }.getOrThrow()

    machine.transitionTo(Letter(B, "barry"), D).getOrThrow() shouldBe Letter(D, "dave")
  }

  @Test
  fun `provides transition context to via blocks`() {
    val machine =
      fsm<String, Letter, Char> {
        A.becomes {
          B.via { (value, from, to) -> value.copy(id = "${from.name} to ${to.name}") }
        }
        B.becomes {
          C.via { (value, from, to) -> value.copy(id = "${from.name} to ${to.name}") }
        }
      }.getOrThrow()

    val result = machine.advance(Letter(A, "start")).getOrThrow()
    result.id shouldBe "A to B"

    val nextResult = machine.advance(result).getOrThrow()
    nextResult.id shouldBe "B to C"
  }

  @Test
  fun `disallows becomes block with no targets`() {
    val result =
      fsm<String, Letter, Char> {
        B.becomes {}
      }
    result.exceptionOrNull()!!.message shouldBe "State B defines a `becomes` block with no transitions"
  }

  @Test
  fun `disallows redeclaration of from state`() {
    val result =
      fsm<String, Letter, Char> {
        B.becomes {
          C.via { it.value }
        }
        B.becomes {
          D.via { it.value }
        }
      }
    result.exceptionOrNull()!!.message shouldBe "State B has multiple `becomes` blocks defined"
  }

  @Test
  fun `disallows redeclaration of to state`() {
    val result =
      fsm<String, Letter, Char> {
        B.becomes {
          C.via { it.value }
          C.via { it.value }
        }
      }
    result.exceptionOrNull()!!.message shouldBe "State C already has a transition defined from B"
  }

  @Test
  fun `disallows transitions between states that do not permit them`() {
    val result =
      fsm<String, Letter, Char> {
        C.becomes {
          B.via { it.value }
        }
      }
    result.exceptionOrNull()!!.message shouldBe "State C declares that it cannot transition to B. " +
      "Either the fsm declaration or the State is incorrect"
  }

  @Test
  fun `can optionally define controllers`() {
    val machine =
      fsm<String, Letter, Char> {
        A.becomes {
          B.via { it.value.copy(id = "bedford") }
        }
        B.becomes(selector = { Result.success(C) }) {
          B.via { it.value }
          C.via { it.value.copy(id = "citroën") }
          D.via { it.value }
        }
        C.becomes {
          D.via { it.value.copy(id = "datsun") }
        }
        D.becomes {
          B.via { it.value.copy(id = "bristol") }
          E.via { it.value }
        }
      }.getOrThrow()

    val a = Letter(A, "audi")
    val b = Letter(B, "bedford")
    val c = Letter(C, "citroën")
    val d = Letter(D, "datsun")
    val e = Letter(E, "eagle")

    machine.advance(a).getOrThrow() shouldBe b
    machine.advance(b).getOrThrow() shouldBe c
    machine.advance(c).getOrThrow() shouldBe d
    machine.advance(d).exceptionOrNull()!!.message shouldBe
      "State D has multiple subsequent states, but no NextStateSelector was provided"
    machine.transitionTo(d, B).getOrThrow() shouldBe b.copy(id = "bristol")
    machine.advance(e).exceptionOrNull()!!.message shouldBe "No selector for state E"
  }
}
