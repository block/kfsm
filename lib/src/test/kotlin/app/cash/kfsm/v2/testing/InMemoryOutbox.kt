package app.cash.kfsm.v2.testing

import app.cash.kfsm.v2.Effect
import app.cash.kfsm.v2.Outbox
import app.cash.kfsm.v2.OutboxMessage

/**
 * In-memory implementation of [Outbox] for testing.
 *
 * Example:
 * ```kotlin
 * val outbox = InMemoryOutbox<String, OrderEffect>()
 * val processor = EffectProcessor(outbox, handler, stateMachine, valueLoader)
 * ```
 */
class InMemoryOutbox<ID, Ef : Effect> : Outbox<ID, Ef> {
    private val messages = mutableListOf<OutboxMessage<ID, Ef>>()
    private val processed = mutableSetOf<String>()
    private val failed = mutableMapOf<String, String>()
    private val deadLetters = mutableSetOf<String>()

    /**
     * Add a message to the outbox (for testing setup).
     */
    fun add(message: OutboxMessage<ID, Ef>) {
        messages.add(message)
    }

    /**
     * Get all messages currently in the outbox.
     */
    fun all(): List<OutboxMessage<ID, Ef>> = messages.toList()

    /**
     * Get all processed message IDs.
     */
    fun processedIds(): Set<String> = processed.toSet()

    /**
     * Get all failed messages with their error messages.
     */
    fun failedMessages(): Map<String, String> = failed.toMap()

    /**
     * Clear all messages (for test reset).
     */
    fun clear() {
        messages.clear()
        processed.clear()
        failed.clear()
        deadLetters.clear()
    }

    override fun fetchPending(batchSize: Int, effectTypes: Set<String>?): List<OutboxMessage<ID, Ef>> =
        messages
            .filter { it.id !in processed && it.id !in deadLetters }
            .filter { it.dependsOnEffectId == null || it.dependsOnEffectId in processed }
            .let { pending ->
                if (effectTypes.isNullOrEmpty()) pending
                else pending.filter { it.effect::class.simpleName in effectTypes }
            }
            .take(batchSize)

    override fun isProcessed(id: String): Boolean = id in processed

    override fun findById(id: String): OutboxMessage<ID, Ef>? =
        messages.find { it.id == id }

    override fun markProcessed(id: String) {
        processed.add(id)
    }

    override fun markFailed(id: String, error: String, maxAttempts: Int?) {
        failed[id] = error
        if (maxAttempts != null && (failed.count { it.key == id } >= maxAttempts)) {
            deadLetters.add(id)
        }
    }

    override fun markDeadLetter(id: String, error: String) {
        deadLetters.add(id)
        failed[id] = error
    }

    override fun fetchDeadLetters(batchSize: Int, effectTypes: Set<String>?): List<OutboxMessage<ID, Ef>> =
        messages
            .filter { it.id in deadLetters }
            .let { dead ->
                if (effectTypes.isNullOrEmpty()) dead
                else dead.filter { it.effect::class.simpleName in effectTypes }
            }
            .take(batchSize)

    override fun retryDeadLetter(id: String): Boolean {
        if (id in deadLetters) {
            deadLetters.remove(id)
            failed.remove(id)
            return true
        }
        return false
    }
}
