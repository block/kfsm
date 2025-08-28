package app.cash.kfsm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class StateMachineTest :
  StringSpec({
    val transitioner = object : Transitioner<String, Transition<String, Letter, Char>, Letter, Char>() {}

    "mermaidStateDiagramMarkdown generates correct diagram" {
      // Given a machine with multiple transitions
      val machine =
        fsm(transitioner) {
          A.becomes {
            B.via { it.copy(id = "banana") }
          }
          B.becomes {
            C.via { it.copy(id = "cinnamon") }
            D.via { it.copy(id = "durian") }
            B.via { it.copy(id = "berry") }
          }
          D.becomes {
            E.via { it.copy(id = "eggplant") }
          }
        }.getOrThrow()

      // When generating a diagram starting from A
      val diagram = machine.mermaidStateDiagramMarkdown(A)

      // Then the diagram contains all expected elements
      diagram shouldBe
        """
        |stateDiagram-v2
        |    [*] --> A
        |    A --> B
        |    B --> B
        |    B --> C
        |    B --> D
        |    D --> E
        """.trimMargin()
    }

    "getAvailableTransitions returns all possible transitions from a state" {
      // Given a machine with multiple transitions from state B
      val machine =
        fsm(transitioner) {
          A.becomes {
            B.via { it.copy(id = "banana") }
          }
          B.becomes {
            C.via { it.copy(id = "cinnamon") }
            D.via { it.copy(id = "durian") }
            B.via { it.copy(id = "berry") }
          }
          D.becomes {
            E.via { it.copy(id = "eggplant") }
          }
        }.getOrThrow()

      // When getting available transitions from state B
      val transitions = machine.getAvailableTransitions(B)

      // Then all transitions from B are returned
      transitions.size shouldBe 3
      transitions.map { it.to }.toSet() shouldBe setOf(B, C, D)
    }

    "getAvailableTransitions returns empty set for state with no transitions" {
      // Given a machine with no transitions from state E
      val machine =
        fsm(transitioner) {
          A.becomes {
            B.via { it.copy(id = "banana") }
          }
          B.becomes {
            C.via { it.copy(id = "cinnamon") }
          }
        }.getOrThrow()

      // When getting available transitions from state E
      val transitions = machine.getAvailableTransitions(E)

      // Then an empty set is returned
      transitions shouldBe emptySet()
    }

    "valid transition succeeds" {
      // Given a machine that allows A -> B
      val machine =
        fsm(transitioner) {
          A.becomes {
            B.via { it.copy(id = "beetroot") }
          }
        }.getOrThrow()

      // When transitioning from A to B
      val result = machine.transitionTo(Letter(A, "avocado"), B).getOrThrow()

      // Then the transition succeeds
      result shouldBe Letter(B, "beetroot")
    }

    "invalid transition fails" {
      // Given a machine that allows A -> B
      val machine =
        fsm(transitioner) {
          A.becomes {
            B.via { it.update(B) }
          }
        }.getOrThrow()

      // When attempting an invalid transition A -> C
      val result = machine.transitionTo(Letter(A, "test"), C)

      // Then the transition fails
      result.shouldBeFailure<NoPathToTargetState>() should {
        it.value shouldBe Letter(A, "test")
        it.targetState shouldBe C
      }
    }

    "transition from undefined state fails" {
      // Given a machine with no transitions from C
      val machine =
        fsm(transitioner) {
          A.becomes {
            B.via { it.copy(id = "banana") }
          }
        }.getOrThrow()

      // When attempting to transition from C
      val result = machine.transitionTo(Letter(C, "apple"), D)

      // Then the transition fails
      result.shouldBeFailure<NoPathToTargetState>() should {
        it.value shouldBe Letter(C, "apple")
        it.targetState shouldBe D
      }
    }

    "self-transition succeeds" {
      // Given a machine that allows B -> B
      val machine =
        fsm(transitioner) {
          B.becomes {
            B.via { it }
          }
        }.getOrThrow()

      // When performing a self-transition
      val value = Letter(B, "test")
      val result = machine.transitionTo(value, B)

      // Then the transition succeeds with the same value
      result.shouldBeSuccess() shouldBe value
    }

    "complex transition chain works" {
      // Given a machine with multiple transitions
      val machine =
        fsm(transitioner) {
          A.becomes {
            B.via { it.copy(id = "banana") }
          }
          B.becomes {
            C.via { it.copy(id = "cinnamon") }
            D.via { it.copy(id = "durian") }
          }
          D.becomes {
            E.via { it.copy(id = "eggplant") }
          }
        }.getOrThrow()

      // When performing multiple transitions
      val startValue = Letter(A, "avocado")
      val result1 = machine.transitionTo(startValue, B).getOrThrow()
      val result2 = machine.transitionTo(result1, D).getOrThrow()
      val result3 = machine.transitionTo(result2, E).getOrThrow()

      // Then each transition succeeds
      result1 shouldBe Letter(B, "banana")
      result2 shouldBe Letter(D, "durian")
      result3 shouldBe Letter(E, "eggplant")
    }

    "failed effect is propagated" {
      // Given a machine with a failing effect
      val expectedError = RuntimeException("Test error")
      val machine =
        fsm(transitioner) {
          A.becomes {
            B.via { throw expectedError }
          }
        }.getOrThrow()

      // When transitioning
      val result = machine.transitionTo(Letter(A, "test"), B)

      // Then the effect's error is propagated
      result.shouldBeFailure(expectedError)
    }

    "hooks are called in order" {
      var callOrder = mutableListOf<String>()
      val hookTransitioner =
        object : Transitioner<String, Transition<String, Letter, Char>, Letter, Char>() {
          override fun preHook(
            value: Letter,
            via: Transition<String, Letter, Char>
          ): Result<Unit> {
            callOrder.add("pre")
            return super.preHook(value, via)
          }

          override fun persist(
            from: Char,
            value: Letter,
            via: Transition<String, Letter, Char>
          ): Result<Letter> {
            callOrder.add("persist")
            return super.persist(from, value, via)
          }

          override fun postHook(
            from: Char,
            value: Letter,
            via: Transition<String, Letter, Char>
          ): Result<Unit> {
            callOrder.add("post")
            return super.postHook(from, value, via)
          }
        }

      // Given a machine with a transitioner that tracks hook calls
      val machine =
        fsm(hookTransitioner) {
          A.becomes {
            B.via { it.update(B) }
          }
        }.getOrThrow()

      // When transitioning
      machine.transitionTo(Letter(A, "test"), B)

      // Then hooks are called in the expected order
      callOrder shouldBe listOf("pre", "persist", "post")
    }

    "failed hook propagates error" {
      val hookError = RuntimeException("Hook error")
      val failingTransitioner =
        object : Transitioner<String, Transition<String, Letter, Char>, Letter, Char>() {
          override fun preHook(
            value: Letter,
            via: Transition<String, Letter, Char>
          ): Result<Unit> = Result.failure(hookError)
        }

      // Given a machine with a failing hook
      val machine =
        fsm(failingTransitioner) {
          A.becomes {
            B.via { it.update(B) }
          }
        }.getOrThrow()

      // When transitioning
      val result = machine.transitionTo(Letter(A, "test"), B)

      // Then the hook error is propagated
      result.shouldBeFailure<RuntimeException>() shouldBe hookError
    }
  })
