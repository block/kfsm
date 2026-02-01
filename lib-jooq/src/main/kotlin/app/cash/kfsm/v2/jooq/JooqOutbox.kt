package app.cash.kfsm.v2.jooq

import app.cash.kfsm.v2.Effect
import app.cash.kfsm.v2.Outbox
import app.cash.kfsm.v2.OutboxMessage
import app.cash.kfsm.v2.OutboxStatus
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * A jOOQ-based [Outbox] implementation using SKIP LOCKED for concurrent processing.
 *
 * This implementation claims messages by entity (value_id) to ensure that all effects
 * for a single entity are processed in order by the same processor instance.
 *
 * Features:
 * - Uses SELECT ... FOR UPDATE SKIP LOCKED for lock-free concurrent access
 * - Claims all pending messages for an entity atomically
 * - Preserves per-entity ordering while allowing cross-entity parallelism
 * - Respects effect dependencies via [OutboxMessage.dependsOnEffectId]
 *
 * @param ID The type of unique identifier for values
 * @param Ef The effect type
 * @param dsl The jOOQ DSLContext
 * @param tableName The outbox table name (default: "outbox")
 * @param serializer Serializes/deserializes effects and value IDs
 */
class JooqOutbox<ID : Any, Ef : Effect>(
  private val dsl: DSLContext,
  private val serializer: OutboxSerializer<ID, Ef>,
  private val tableName: String = "outbox"
) : Outbox<ID, Ef> {

  private val table = DSL.table(DSL.name(tableName))
  private val idField = DSL.field(DSL.name("id"), String::class.java)
  private val valueIdField = DSL.field(DSL.name("value_id"), String::class.java)
  private val effectTypeField = DSL.field(DSL.name("effect_type"), String::class.java)
  private val effectPayloadField = DSL.field(DSL.name("effect_payload"), String::class.java)
  private val dedupKeyField = DSL.field(DSL.name("dedup_key"), String::class.java)
  private val dependsOnField = DSL.field(DSL.name("depends_on_effect_id"), String::class.java)
  private val statusField = DSL.field(DSL.name("status"), String::class.java)
  private val attemptCountField = DSL.field(DSL.name("attempt_count"), Int::class.java)
  private val lastErrorField = DSL.field(DSL.name("last_error"), String::class.java)
  private val createdAtField = DSL.field(DSL.name("created_at"), Any::class.java)
  private val processedAtField = DSL.field(DSL.name("processed_at"), Any::class.java)

  override fun fetchPending(batchSize: Int, effectTypes: Set<String>?): List<OutboxMessage<ID, Ef>> {
    return dsl.transactionResult { config ->
      val tx = DSL.using(config)
      claimNextEntityMessages(tx, batchSize, effectTypes)
    }
  }

  /**
   * Fetch pending messages for a specific entity.
   * Useful when you know which entity to process (e.g., from CDC event).
   */
  fun fetchPendingForEntity(valueId: ID, effectTypes: Set<String>? = null): List<OutboxMessage<ID, Ef>> {
    val serializedId = serializer.serializeId(valueId)
    return dsl.transactionResult { config ->
      val tx = DSL.using(config)
      fetchMessagesForEntity(tx, serializedId, effectTypes = effectTypes)
    }
  }

  /**
   * Claims messages for the next available entity using SKIP LOCKED.
   * All pending messages for that entity are returned in order.
   */
  private fun claimNextEntityMessages(
    tx: DSLContext,
    limit: Int,
    effectTypes: Set<String>?
  ): List<OutboxMessage<ID, Ef>> {
    // First, claim one entity by selecting a distinct value_id with SKIP LOCKED
    var query = tx.select(valueIdField)
      .from(table)
      .where(statusField.eq(OutboxStatus.PENDING.name))
      .and(dependencyProcessedCondition())

    if (!effectTypes.isNullOrEmpty()) {
      query = query.and(effectTypeField.`in`(effectTypes))
    }

    val claimedValueId = query
      .orderBy(createdAtField)
      .limit(1)
      .forUpdate()
      .skipLocked()
      .fetchOne(valueIdField)

    // Then fetch all pending messages for that entity in order
    return claimedValueId?.let { fetchMessagesForEntity(tx, it, limit, effectTypes) } ?: emptyList()
  }

  private fun fetchMessagesForEntity(
    tx: DSLContext, 
    serializedValueId: String, 
    limit: Int = Int.MAX_VALUE,
    effectTypes: Set<String>? = null
  ): List<OutboxMessage<ID, Ef>> {
    var query = tx.selectFrom(table)
      .where(valueIdField.eq(serializedValueId))
      .and(statusField.eq(OutboxStatus.PENDING.name))
      .and(dependencyProcessedCondition())

    if (!effectTypes.isNullOrEmpty()) {
      query = query.and(effectTypeField.`in`(effectTypes))
    }

    return query
      .orderBy(createdAtField)
      .limit(limit)
      .forUpdate()
      .fetch()
      .map { record -> recordToMessage(record) }
  }

  private fun dependencyProcessedCondition() = DSL.or(
    dependsOnField.isNull,
    DSL.notExists(
      DSL.selectOne()
        .from(table)
        .where(idField.eq(dependsOnField))
        .and(statusField.ne(OutboxStatus.PROCESSED.name))
    )
  )

  override fun isProcessed(id: String): Boolean {
    return (dsl.selectCount()
      .from(table)
      .where(idField.eq(id))
      .and(statusField.eq(OutboxStatus.PROCESSED.name))
      .fetchOne(0, Int::class.java) ?: 0) > 0
  }

  override fun findById(id: String): OutboxMessage<ID, Ef>? {
    return dsl.selectFrom(table)
      .where(idField.eq(id))
      .fetchOne()
      ?.let { recordToMessage(it) }
  }

  override fun markProcessed(id: String) {
    dsl.update(table)
      .set(statusField, OutboxStatus.PROCESSED.name)
      .set(processedAtField, Instant.now())
      .where(idField.eq(id))
      .execute()
  }

  override fun markFailed(id: String, error: String, maxAttempts: Int?) {
    if (maxAttempts != null) {
      // Check current attempt count and move to dead letter if exceeded
      val currentAttempts = dsl.select(attemptCountField)
        .from(table)
        .where(idField.eq(id))
        .fetchOne(attemptCountField) ?: 0

      if (currentAttempts + 1 >= maxAttempts) {
        markDeadLetter(id, error)
        return
      }
    }

    dsl.update(table)
      .set(statusField, OutboxStatus.FAILED.name)
      .set(lastErrorField, error.take(4000))
      .set(attemptCountField, attemptCountField.plus(1))
      .where(idField.eq(id))
      .execute()
  }

  override fun markDeadLetter(id: String, error: String) {
    dsl.update(table)
      .set(statusField, OutboxStatus.DEAD_LETTER.name)
      .set(lastErrorField, error.take(4000))
      .set(attemptCountField, attemptCountField.plus(1))
      .where(idField.eq(id))
      .execute()
  }

  override fun fetchDeadLetters(batchSize: Int, effectTypes: Set<String>?): List<OutboxMessage<ID, Ef>> {
    var query = dsl.selectFrom(table)
      .where(statusField.eq(OutboxStatus.DEAD_LETTER.name))

    if (!effectTypes.isNullOrEmpty()) {
      query = query.and(effectTypeField.`in`(effectTypes))
    }

    return query
      .orderBy(createdAtField)
      .limit(batchSize)
      .fetch()
      .map { record -> recordToMessage(record) }
  }

  override fun retryDeadLetter(id: String): Boolean {
    val updated = dsl.update(table)
      .set(statusField, OutboxStatus.PENDING.name)
      .set(attemptCountField, 0)
      .set(lastErrorField, null as String?)
      .where(idField.eq(id))
      .and(statusField.eq(OutboxStatus.DEAD_LETTER.name))
      .execute()

    return updated > 0
  }

  /**
   * Insert outbox messages. Called during state transitions.
   * Should be executed within the same transaction as the state change.
   */
  fun insert(messages: List<OutboxMessage<ID, Ef>>) {
    insertWithContext(dsl, messages)
  }

  /**
   * Insert outbox messages within a provided transaction context.
   */
  fun insert(tx: DSLContext, messages: List<OutboxMessage<ID, Ef>>) {
    insertWithContext(tx, messages)
  }

  private fun insertWithContext(ctx: DSLContext, messages: List<OutboxMessage<ID, Ef>>) {
    if (messages.isEmpty()) return

    val insert = ctx.insertInto(
      table,
      idField, valueIdField, effectTypeField, effectPayloadField,
      dedupKeyField, dependsOnField, statusField, attemptCountField,
      lastErrorField, createdAtField
    )

    messages.fold(insert) { acc, msg ->
      acc.values(
        msg.id,
        serializer.serializeId(msg.valueId),
        msg.type,
        serializer.serializeEffect(msg.effect),
        msg.dedupKey,
        msg.dependsOnEffectId,
        msg.status.name,
        msg.attemptCount,
        msg.lastError,
        msg.createdAt
      )
    }.execute()
  }

  /**
   * Retry failed messages by resetting their status to PENDING.
   *
   * @param maxAttempts Only retry messages with fewer attempts than this
   * @param olderThan Only retry messages that failed before this time
   * @return Number of messages reset
   */
  fun retryFailed(maxAttempts: Int = 3, olderThan: Instant = Instant.now()): Int {
    return dsl.update(table)
      .set(statusField, OutboxStatus.PENDING.name)
      .where(statusField.eq(OutboxStatus.FAILED.name))
      .and(attemptCountField.lt(maxAttempts))
      .and(createdAtField.lt(olderThan))
      .execute()
  }

  /**
   * Delete old processed messages.
   *
   * @param olderThan Delete messages processed before this time
   * @return Number of messages deleted
   */
  fun deleteProcessed(olderThan: Instant): Int {
    return dsl.deleteFrom(table)
      .where(statusField.eq(OutboxStatus.PROCESSED.name))
      .and(processedAtField.lt(olderThan))
      .execute()
  }

  /**
   * Get counts by status for monitoring.
   */
  fun getStatusCounts(): Map<OutboxStatus, Int> {
    return dsl.select(statusField, DSL.count())
      .from(table)
      .groupBy(statusField)
      .fetch()
      .associate { record ->
        OutboxStatus.valueOf(record.value1()!!) to record.value2()
      }
  }

  @Suppress("UNCHECKED_CAST")
  private fun recordToMessage(record: org.jooq.Record): OutboxMessage<ID, Ef> {
    val effectType = record.get(effectTypeField)!!
    val effectPayload = record.get(effectPayloadField)!!
    val serializedValueId = record.get(valueIdField)!!

    return OutboxMessage(
      id = record.get(idField)!!,
      valueId = serializer.deserializeId(serializedValueId),
      effect = serializer.deserializeEffect(effectType, effectPayload),
      type = effectType,
      dedupKey = record.get(dedupKeyField),
      dependsOnEffectId = record.get(dependsOnField),
      status = OutboxStatus.valueOf(record.get(statusField)!!),
      attemptCount = record.get(attemptCountField) ?: 0,
      lastError = record.get(lastErrorField),
      createdAt = record.get(createdAtField)?.toInstantSafe() ?: Instant.now()
    )
  }

  private fun Any.toInstantSafe(): Instant = when (this) {
    is Instant -> this
    is Timestamp -> this.toInstant()
    is LocalDateTime -> this.toInstant(ZoneOffset.UTC)
    else -> throw IllegalArgumentException("Cannot convert $this to Instant")
  }
}
