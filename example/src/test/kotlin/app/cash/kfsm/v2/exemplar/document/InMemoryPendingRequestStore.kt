package app.cash.kfsm.v2.exemplar.document

import app.cash.kfsm.v2.PendingRequestStatus
import app.cash.kfsm.v2.PendingRequestStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryPendingRequestStore<ID, V> : PendingRequestStore<ID, V> {

  private data class Entry<V>(
    val valueId: Any,
    var status: PendingRequestStatus<V>
  )

  private val requests = ConcurrentHashMap<String, Entry<V>>()
  private val valueIdToRequestIds = ConcurrentHashMap<Any, MutableSet<String>>()

  override fun create(valueId: ID): Result<String> {
    val requestId = UUID.randomUUID().toString()
    requests[requestId] = Entry(valueId as Any, PendingRequestStatus.Waiting)
    valueIdToRequestIds.computeIfAbsent(valueId as Any) { mutableSetOf() }.add(requestId)
    return Result.success(requestId)
  }

  override fun getStatus(requestId: String): Result<PendingRequestStatus<V>> {
    return Result.success(requests[requestId]?.status ?: PendingRequestStatus.NotFound)
  }

  override fun complete(valueId: ID, value: V): Result<Unit> {
    val requestIds = valueIdToRequestIds[valueId as Any] ?: return Result.success(Unit)
    requestIds.forEach { requestId ->
      requests[requestId]?.let { entry ->
        if (entry.status is PendingRequestStatus.Waiting) {
          entry.status = PendingRequestStatus.Completed(value)
        }
      }
    }
    return Result.success(Unit)
  }

  override fun fail(valueId: ID, error: String): Result<Unit> {
    val requestIds = valueIdToRequestIds[valueId as Any] ?: return Result.success(Unit)
    requestIds.forEach { requestId ->
      requests[requestId]?.let { entry ->
        if (entry.status is PendingRequestStatus.Waiting) {
          entry.status = PendingRequestStatus.Failed(error)
        }
      }
    }
    return Result.success(Unit)
  }

  override fun timeout(requestId: String): Result<Unit> {
    requests[requestId]?.let { entry ->
      if (entry.status is PendingRequestStatus.Waiting) {
        entry.status = PendingRequestStatus.Failed("Timed out")
      }
    }
    return Result.success(Unit)
  }

  override fun delete(requestId: String): Result<Unit> {
    val entry = requests.remove(requestId)
    if (entry != null) {
      valueIdToRequestIds[entry.valueId]?.remove(requestId)
    }
    return Result.success(Unit)
  }

  fun clear() {
    requests.clear()
    valueIdToRequestIds.clear()
  }
}
