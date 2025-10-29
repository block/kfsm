package app.cash.kfsm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

class OutboxTest : StringSpec({

  "captures deferrable effects in outbox instead of executing immediately" {
    val outboxHandler = InMemoryOutboxHandler<String, Letter, Char>()
    val transition = DeferrableLetterTransition(from = A, to = B, effectData = "test-effect")

    // Variable to capture messages during persistence
    var capturedMessages: List<OutboxMessage<String>>? = null

    val transitioner = object : Transitioner<String, LetterTransition, Letter, Char>() {
      override val outboxHandler = outboxHandler

      override fun persist(from: Char, value: Letter, via: LetterTransition): Result<Letter> =
        Result.success(value)

      override fun persistWithOutbox(
        from: Char,
        value: Letter,
        via: LetterTransition,
        outboxMessages: List<OutboxMessage<String>>
      ): Result<Letter> {
        // Capture the messages for verification
        capturedMessages = outboxMessages
        // In a real implementation, you would persist both the value and outbox messages in a transaction
        return Result.success(value)
      }
    }

    val letter = Letter(A, "letter-1")
    val result = transitioner.transition(letter, transition)

    // Verify transition succeeded
    result shouldBeSuccess Letter(B, "letter-1")

    // Verify effect was NOT executed immediately
    transition.effectExecuted shouldBe false

    // Verify effect was captured in outbox
    capturedMessages!! shouldHaveSize 1

    val message = capturedMessages!!.first()
    message.valueId shouldBe "letter-1"
    message.effectPayload.effectType shouldBe "letter-transition"
    message.effectPayload.data shouldBe "test-effect"
    message.status shouldBe OutboxStatus.PENDING
  }

  "executes regular effects immediately when not deferrable" {
    val outboxHandler = InMemoryOutboxHandler<String, Letter, Char>()
    val transition = RegularLetterTransition(from = A, to = B)

    // Variable to capture messages during persistence
    var capturedMessages: List<OutboxMessage<String>>? = null

    val transitioner = object : Transitioner<String, LetterTransition, Letter, Char>() {
      override val outboxHandler = outboxHandler

      override fun persist(from: Char, value: Letter, via: LetterTransition): Result<Letter> =
        Result.success(value)

      override fun persistWithOutbox(
        from: Char,
        value: Letter,
        via: LetterTransition,
        outboxMessages: List<OutboxMessage<String>>
      ): Result<Letter> {
        capturedMessages = outboxMessages
        return Result.success(value)
      }
    }

    val letter = Letter(A, "letter-2")
    val result = transitioner.transition(letter, transition)

    // Verify transition succeeded
    result shouldBeSuccess Letter(B, "letter-2")

    // Verify effect WAS executed immediately
    transition.effectExecuted shouldBe true

    // Verify nothing was captured in outbox
    capturedMessages!! shouldHaveSize 0
  }
})

// Test transitions
private class DeferrableLetterTransition(
  from: Char,
  to: Char,
  private val effectData: String
) : LetterTransition(from, to), DeferrableEffect<String, Letter, Char> {

  var effectExecuted = false

  override fun effect(value: Letter): Result<Letter> {
    effectExecuted = true
    return Result.success(value.update(to))
  }

  override fun serialize(value: Letter): Result<EffectPayload> = Result.success(
    EffectPayload(
      effectType = "letter-transition",
      data = effectData
    )
  )

  override val effectType = "letter-transition"
}

private class RegularLetterTransition(
  from: Char,
  to: Char
) : LetterTransition(from, to) {

  var effectExecuted = false

  override fun effect(value: Letter): Result<Letter> {
    effectExecuted = true
    return Result.success(value.update(to))
  }
}

// Simple in-memory outbox handler for testing
private class InMemoryOutboxHandler<ID, V : Value<ID, V, S>, S : State<ID, V, S>> : OutboxHandler<ID, V, S> {

  private val messages = mutableListOf<OutboxMessage<ID>>()

  override fun captureEffect(value: V, effect: DeferrableEffect<ID, V, S>): Result<V> {
    val payload = effect.serialize(value).getOrElse { return Result.failure(it) }
    val message = OutboxMessage(
      id = "msg-${messages.size + 1}",
      valueId = value.id,
      effectPayload = payload,
      createdAt = System.currentTimeMillis()
    )
    messages.add(message)
    return Result.success(value)
  }

  override fun getPendingMessages(): List<OutboxMessage<ID>> = messages.toList()

  override fun clearPending() {
    messages.clear()
  }
}
