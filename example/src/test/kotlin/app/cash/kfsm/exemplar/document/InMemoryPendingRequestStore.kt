package app.cash.kfsm.exemplar.document

import app.cash.kfsm.PendingRequestStatus
import app.cash.kfsm.PendingRequestStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryPendingRequestStore<ID, V> : PendingRequestStore<ID, V> {

  private data class Entry<V>(
    val valueId: Any,
    var status: PendingRequestStatus<V>
  )

  private val requests = ConcurrentHashMap<String, Entry<V>>()
  private val valueIdToRequestIds = ConcurrentHashMap<Any, MutableSet<String>>()

  override fun create(valueId: ID): String {
    val requestId = UUID.randomUUID().toString()
    requests[requestId] = Entry(valueId as Any, PendingRequestStatus.Waiting)
    valueIdToRequestIds.computeIfAbsent(valueId as Any) { mutableSetOf() }.add(requestId)
    return requestId
  }

  override fun getStatus(requestId: String): PendingRequestStatus<V> {
    return requests[requestId]?.status ?: PendingRequestStatus.NotFound
  }

  override fun complete(valueId: ID, value: V) {
    val requestIds = valueIdToRequestIds[valueId as Any] ?: return
    requestIds.forEach { requestId ->
      requests[requestId]?.let { entry ->
        if (entry.status is PendingRequestStatus.Waiting) {
          entry.status = PendingRequestStatus.Completed(value)
        }
      }
    }
  }

  override fun fail(valueId: ID, error: String) {
    val requestIds = valueIdToRequestIds[valueId as Any] ?: return
    requestIds.forEach { requestId ->
      requests[requestId]?.let { entry ->
        if (entry.status is PendingRequestStatus.Waiting) {
          entry.status = PendingRequestStatus.Failed(error)
        }
      }
    }
  }

  override fun markTimedOut(requestId: String) {
    requests[requestId]?.let { entry ->
      if (entry.status is PendingRequestStatus.Waiting) {
        entry.status = PendingRequestStatus.Failed("Timed out")
      }
    }
  }

  override fun delete(requestId: String) {
    val entry = requests.remove(requestId)
    if (entry != null) {
      valueIdToRequestIds[entry.valueId]?.remove(requestId)
    }
  }

  fun clear() {
    requests.clear()
    valueIdToRequestIds.clear()
  }
}
