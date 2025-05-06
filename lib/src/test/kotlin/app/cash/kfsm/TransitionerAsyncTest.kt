package app.cash.kfsm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage

class TransitionerAsyncTest : StringSpec({

  fun transitioner(
    pre: (Letter, LetterTransition) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    post: (Char, Letter, LetterTransition) -> Result<Unit> = { _, _, _ -> Result.success(Unit) },
    persist: (Char, Letter, LetterTransition) -> Result<Letter> = { _, value, _ -> Result.success(value) },
  ) = object : TransitionerAsync<String, LetterTransition, Letter, Char>() {
    var preHookExecuted = 0
    var postHookExecuted = 0

    override suspend fun preHook(value: Letter, via: LetterTransition): Result<Unit> =
      pre(value, via).also { preHookExecuted += 1 }

    override suspend fun persist(from: Char, value: Letter, via: LetterTransition): Result<Letter> =
      persist(from, value, via)

    override suspend fun postHook(from: Char, value: Letter, via: LetterTransition): Result<Unit> =
      post(from, value, via).also { postHookExecuted += 1 }
  }

  fun transition(from: Char = A, to: Char = B) = object : LetterTransition(from, to) {
    var effected = 0
    override suspend fun effectAsync(value: Letter): Result<Letter> {
      effected += 1
      return Result.success(value.update(to))
    }
  }

  "effects valid transition" {
    val transition = transition(from = A, to = B)
    val transitioner = transitioner()

    transitioner.transition(Letter(A, "my_letter"), transition) shouldBeSuccess Letter(B, "my_letter")

    transitioner.preHookExecuted shouldBe 1
    transition.effected shouldBe 1
    transitioner.postHookExecuted shouldBe 1
  }

  "ignores completed transition" {
    val transition = transition(from = A, to = B)
    val transitioner = transitioner()

    transitioner.transition(Letter(B, "letter_02"), transition) shouldBeSuccess Letter(B, "letter_02")

    transitioner.preHookExecuted shouldBe 0
    transition.effected shouldBe 0
    transitioner.postHookExecuted shouldBe 0
  }

  "returns error on invalid transition" {
    val transition = transition(from = A, to = B)
    val transitioner = transitioner()

    transitioner.transition(Letter(C, "letter_03"), transition).shouldBeFailure()
      .shouldHaveMessage("Value cannot transition {A} to B, because it is currently C. [id=letter_03]")

    transitioner.preHookExecuted shouldBe 0
    transition.effected shouldBe 0
    transitioner.postHookExecuted shouldBe 0
  }

  "returns error when preHook errors" {
    val error = RuntimeException("preHook error")

    val transition = transition(from = A, to = B)
    val transitioner = transitioner(pre = { _, _ -> Result.failure(error) })

    transitioner.transition(Letter(A, "letter_04"), transition) shouldBeFailure error

    transitioner.preHookExecuted shouldBe 1
    transition.effected shouldBe 0
    transitioner.postHookExecuted shouldBe 0
  }

  "returns error when effect errors" {
    val error = RuntimeException("effect error")

    val transition = object : LetterTransition(A, B) {
      override suspend fun effectAsync(value: Letter): Result<Letter> = Result.failure(error)
    }
    val transitioner = transitioner()

    transitioner.transition(Letter(A, "letter_05"), transition) shouldBeFailure error

    transitioner.preHookExecuted shouldBe 1
    transitioner.postHookExecuted shouldBe 0
  }

  "returns error when postHook errors" {
    val error = RuntimeException("postHook error")

    val transition = transition(from = A, to = B)
    val transitioner = transitioner(post = { _, _, _ -> Result.failure(error) })

    transitioner.transition(Letter(A, "letter_06"), transition) shouldBeFailure error

    transition.effected shouldBe 1
    transitioner.preHookExecuted shouldBe 1
    transitioner.postHookExecuted shouldBe 1
  }

  "returns error when preHook throws" {
    val error = RuntimeException("preHook error")

    val transition = transition(from = A, to = B)
    val transitioner = transitioner(pre = { _, _ -> throw error })

    transitioner.transition(Letter(A, "letter_07"), transition) shouldBeFailure error

    transition.effected shouldBe 0
    transitioner.postHookExecuted shouldBe 0
  }

  "returns error when effect throws" {
    val error = RuntimeException("effect error")

    val transition = object : LetterTransition(A, B) {
      override fun effect(value: Letter): Result<Letter> = throw error
    }
    val transitioner = transitioner()

    transitioner.transition(Letter(A, "letter_08"), transition) shouldBeFailure error

    transitioner.preHookExecuted shouldBe 1
    transitioner.postHookExecuted shouldBe 0
  }

  "returns error when postHook throws" {
    val error = RuntimeException("postHook error")

    val transition = transition(from = A, to = B)
    val transitioner = transitioner(post = { _, _, _ -> throw error })

    transitioner.transition(Letter(A, "letter_09"), transition) shouldBeFailure error

    transition.effected shouldBe 1
    transitioner.preHookExecuted shouldBe 1
  }

  "returns error when persist fails" {
    val error = RuntimeException("persist error")

    val transition = transition(from = A, to = B)
    val transitioner = transitioner(persist = { _, _, _ -> Result.failure(error) })

    transitioner.transition(Letter(A, "letter_0a"), transition) shouldBeFailure error

    transitioner.preHookExecuted shouldBe 1
    transition.effected shouldBe 1
    transitioner.postHookExecuted shouldBe 0
  }

  "returns error when persist throws" {
    val error = RuntimeException("persist error")

    val transition = transition(from = A, to = B)
    val transitioner = transitioner(persist = { _, _, _ -> throw error })

    transitioner.transition(Letter(A, "letter_0b"), transition) shouldBeFailure error

    transitioner.preHookExecuted shouldBe 1
    transition.effected shouldBe 1
    transitioner.postHookExecuted shouldBe 0
  }

  "can transition multiple times" {
    val aToB = transition(A, B)
    val bToC = transition(B, C)
    val cToD = transition(C, D)
    val dToE = transition(D, E)
    val transitioner = transitioner()

    transitioner.transition(Letter(A, "letter_0c"), aToB)
      .mapCatching { transitioner.transition(it, bToC).getOrThrow() }
      .mapCatching { transitioner.transition(it, cToD).getOrThrow() }
      .mapCatching { transitioner.transition(it, dToE).getOrThrow() } shouldBeSuccess Letter(E, "letter_0c")

    transitioner.preHookExecuted shouldBe 4
    aToB.effected shouldBe 1
    bToC.effected shouldBe 1
    cToD.effected shouldBe 1
    dToE.effected shouldBe 1
    transitioner.postHookExecuted shouldBe 4
  }

  "can transition in a 3+ party loop" {
    val aToB = transition(A, B)
    val bToC = transition(B, C)
    val cToD = transition(C, D)
    val dToB = transition(D, B)
    val dToE = transition(D, E)
    val transitioner = transitioner()

    transitioner.transition(Letter(A, "letter_0d"), aToB)
      .mapCatching { transitioner.transition(it, bToC).getOrThrow() }
      .mapCatching { transitioner.transition(it, cToD).getOrThrow() }
      .mapCatching { transitioner.transition(it, dToB).getOrThrow() }
      .mapCatching { transitioner.transition(it, bToC).getOrThrow() }
      .mapCatching { transitioner.transition(it, cToD).getOrThrow() }
      .mapCatching { transitioner.transition(it, dToE).getOrThrow() } shouldBeSuccess Letter(E, "letter_0d")

    transitioner.preHookExecuted shouldBe 7
    aToB.effected shouldBe 1
    bToC.effected shouldBe 2
    cToD.effected shouldBe 2
    dToB.effected shouldBe 1
    dToE.effected shouldBe 1
    transitioner.postHookExecuted shouldBe 7
  }

  "can transition in a 2-party loop" {
    val aToB = transition(A, B)
    val bToD = transition(B, D)
    val dToB = transition(D, B)
    val dToE = transition(D, E)
    val transitioner = transitioner()

    transitioner.transition(Letter(A, "letter_0e"), aToB)
      .mapCatching { transitioner.transition(it, bToD).getOrThrow() }
      .mapCatching { transitioner.transition(it, dToB).getOrThrow() }
      .mapCatching { transitioner.transition(it, bToD).getOrThrow() }
      .mapCatching { transitioner.transition(it, dToE).getOrThrow() } shouldBeSuccess Letter(E, "letter_0e")

    transitioner.preHookExecuted shouldBe 5
    aToB.effected shouldBe 1
    bToD.effected shouldBe 2
    dToB.effected shouldBe 1
    dToE.effected shouldBe 1
    transitioner.postHookExecuted shouldBe 5
  }

  "can transition to self" {
    val aToB = transition(A, B)
    val bToB = transition(B, B)
    val bToC = transition(B, C)
    val transitioner = transitioner()

    transitioner.transition(Letter(A, "letter_0f"), aToB)
      .mapCatching { transitioner.transition(it, bToB).getOrThrow() }
      .mapCatching { transitioner.transition(it, bToB).getOrThrow() }
      .mapCatching { transitioner.transition(it, bToB).getOrThrow() }
      .mapCatching { transitioner.transition(it, bToC).getOrThrow() } shouldBeSuccess Letter(C, "letter_0f")

    transitioner.preHookExecuted shouldBe 5
    aToB.effected shouldBe 1
    bToB.effected shouldBe 3
    bToC.effected shouldBe 1
    transitioner.postHookExecuted shouldBe 5
  }

  "pre hook contains the correct from value and transition" {
    val transition = transition(from = A, to = B)
    val transitioner = transitioner(
      pre = { value, t ->
        value shouldBe Letter(A, "letter_10")
        t shouldBe transition
        t.specificToThisTransitionType shouldBe "[A] -> B"
        Result.success(Unit)
      }
    )

    transitioner.transition(Letter(A, "letter_10"), transition).shouldBeSuccess()
  }

  "post hook contains the correct from state, post value and transition" {
    val transition = transition(from = B, to = C)
    val transitioner = transitioner(
      post = { from, value, t ->
        from shouldBe B
        value shouldBe Letter(C, "letter_11")
        t shouldBe transition
        t.specificToThisTransitionType shouldBe "[B] -> C"
        Result.success(Unit)
      }
    )

    transitioner.transition(Letter(B, "letter_11"), transition).shouldBeSuccess()
  }
})

