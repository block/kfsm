package app.cash.kfsm.v2.testing

import app.cash.kfsm.v2.PendingRequestStatus
import app.cash.kfsm.v2.PendingRequestStore
import java.util.UUID

/**
 * In-memory implementation of [PendingRequestStore] for testing.
 *
 * Example:
 * ```kotlin
 * val store = InMemoryPendingRequestStore<String, Order>()
 * val awaitable = AwaitableStateMachine(stateMachine, store, settledStates)
 * ```
 */
class InMemoryPendingRequestStore<ID, V> : PendingRequestStore<ID, V> {
    private val requests = mutableMapOf<String, RequestEntry<ID, V>>()

    private data class RequestEntry<ID, V>(
        val valueId: ID,
        var status: PendingRequestStatus<V>
    )

    /**
     * Get all pending request IDs.
     */
    fun allRequestIds(): Set<String> = requests.keys.toSet()

    /**
     * Get the number of pending requests.
     */
    fun size(): Int = requests.size

    /**
     * Check if a request exists.
     */
    fun exists(requestId: String): Boolean = requestId in requests

    /**
     * Clear all requests (for test reset).
     */
    fun clear() {
        requests.clear()
    }

    override fun create(valueId: ID): String {
        val id = "req-${UUID.randomUUID()}"
        requests[id] = RequestEntry(valueId, PendingRequestStatus.Waiting)
        return id
    }

    override fun getStatus(requestId: String): PendingRequestStatus<V> =
        requests[requestId]?.status ?: PendingRequestStatus.NotFound

    override fun complete(valueId: ID, value: V) {
        requests.entries
            .filter { it.value.valueId == valueId && it.value.status is PendingRequestStatus.Waiting }
            .forEach { it.value.status = PendingRequestStatus.Completed(value) }
    }

    override fun fail(valueId: ID, error: String) {
        requests.entries
            .filter { it.value.valueId == valueId && it.value.status is PendingRequestStatus.Waiting }
            .forEach { it.value.status = PendingRequestStatus.Failed(error) }
    }

    override fun timeout(requestId: String) {
        requests.remove(requestId)
    }

    override fun delete(requestId: String) {
        requests.remove(requestId)
    }
}
