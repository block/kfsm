package app.cash.kfsm.v2.jooq

import app.cash.kfsm.v2.PendingRequestStatus
import app.cash.kfsm.v2.PendingRequestStore
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.Instant
import java.util.UUID

/**
 * A jOOQ-based [PendingRequestStore] implementation.
 *
 * Stores pending requests in a database table so that any instance can mark
 * a request complete and any instance can poll for results.
 *
 * @param ID The type of unique identifier for values
 * @param V The value type
 * @param dsl The jOOQ DSLContext
 * @param serializer Serializes/deserializes value IDs and values
 * @param tableName The pending requests table name (default: "pending_requests")
 */
class JooqPendingRequestStore<ID, V>(
  private val dsl: DSLContext,
  private val serializer: PendingRequestSerializer<ID, V>,
  private val tableName: String = "pending_requests"
) : PendingRequestStore<ID, V> {

  private val table = DSL.table(DSL.name(tableName))
  private val idField = DSL.field(DSL.name("id"), String::class.java)
  private val valueIdField = DSL.field(DSL.name("value_id"), String::class.java)
  private val statusField = DSL.field(DSL.name("status"), String::class.java)
  private val resultPayloadField = DSL.field(DSL.name("result_payload"), String::class.java)
  private val createdAtField = DSL.field(DSL.name("created_at"), Any::class.java)
  private val updatedAtField = DSL.field(DSL.name("updated_at"), Any::class.java)

  companion object {
    private const val STATUS_WAITING = "WAITING"
    private const val STATUS_COMPLETED = "COMPLETED"
    private const val STATUS_FAILED = "FAILED"
  }

  override fun create(valueId: ID): String {
    val requestId = UUID.randomUUID().toString()
    dsl.insertInto(
      table,
      idField, valueIdField, statusField, createdAtField
    ).values(
      requestId,
      serializer.serializeId(valueId),
      STATUS_WAITING,
      Instant.now()
    ).execute()
    return requestId
  }

  override fun getStatus(requestId: String): PendingRequestStatus<V> {
    val record = dsl.select(statusField, resultPayloadField)
      .from(table)
      .where(idField.eq(requestId))
      .fetchOne()
      ?: return PendingRequestStatus.NotFound

    return when (record.get(statusField)) {
      STATUS_WAITING -> PendingRequestStatus.Waiting
      STATUS_COMPLETED -> {
        val payload = record.get(resultPayloadField)!!
        PendingRequestStatus.Completed(serializer.deserializeValue(payload))
      }
      STATUS_FAILED -> {
        val error = record.get(resultPayloadField) ?: "Unknown error"
        PendingRequestStatus.Failed(error)
      }
      else -> PendingRequestStatus.NotFound
    }
  }

  override fun complete(valueId: ID, value: V) {
    dsl.update(table)
      .set(statusField, STATUS_COMPLETED)
      .set(resultPayloadField, serializer.serializeValue(value))
      .set(updatedAtField, Instant.now())
      .where(valueIdField.eq(serializer.serializeId(valueId)))
      .and(statusField.eq(STATUS_WAITING))
      .execute()
  }

  override fun fail(valueId: ID, error: String) {
    dsl.update(table)
      .set(statusField, STATUS_FAILED)
      .set(resultPayloadField, error)
      .set(updatedAtField, Instant.now())
      .where(valueIdField.eq(serializer.serializeId(valueId)))
      .and(statusField.eq(STATUS_WAITING))
      .execute()
  }

  override fun timeout(requestId: String) {
    dsl.update(table)
      .set(statusField, STATUS_FAILED)
      .set(resultPayloadField, "Timed out")
      .set(updatedAtField, Instant.now())
      .where(idField.eq(requestId))
      .and(statusField.eq(STATUS_WAITING))
      .execute()
  }

  override fun delete(requestId: String) {
    dsl.deleteFrom(table)
      .where(idField.eq(requestId))
      .execute()
  }

  /**
   * Delete pending requests older than the given instant.
   *
   * Useful for cleaning up stale requests that were never completed or deleted.
   *
   * @param olderThan Delete requests created before this time
   * @return Number of requests deleted
   */
  fun deleteOlderThan(olderThan: Instant): Int {
    return dsl.deleteFrom(table)
      .where(createdAtField.lt(olderThan))
      .execute()
  }
}
