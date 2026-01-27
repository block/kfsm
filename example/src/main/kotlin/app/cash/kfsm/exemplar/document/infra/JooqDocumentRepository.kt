package app.cash.kfsm.exemplar.document.infra

import app.cash.kfsm.exemplar.document.DocumentEffect
import app.cash.kfsm.exemplar.document.DocumentState
import app.cash.kfsm.exemplar.document.DocumentUpload
import app.cash.kfsm.OutboxMessage
import app.cash.kfsm.Repository
import app.cash.kfsm.jooq.JooqOutbox
import org.jooq.DSLContext
import org.jooq.JSON
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.jooq.impl.SQLDataType
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

private fun Any.toInstantSafe(): Instant = when (this) {
  is Timestamp -> this.toInstant()
  is LocalDateTime -> this.toInstant(ZoneOffset.UTC)
  else -> throw IllegalArgumentException("Cannot convert $this to Instant")
}

/**
 * jOOQ implementation of the Repository interface.
 *
 * Stores document state and outbox messages atomically in the same transaction.
 * Uses lib-jooq's JooqOutbox for outbox operations.
 */
class JooqDocumentRepository(
  private val dsl: DSLContext,
  private val outbox: JooqOutbox<String, DocumentEffect> = JooqOutbox(
    dsl = dsl,
    serializer = DocumentOutboxSerializer.instance,
    tableName = "outbox_messages"
  )
) : Repository<String, DocumentUpload, DocumentState, DocumentEffect> {

  companion object {
    private val DOCUMENTS = table("document_uploads")

    // Document fields
    private val DOC_ID = field("id", SQLDataType.VARCHAR(36))
    private val DOC_STATE = field("state", SQLDataType.VARCHAR(50))
    private val DOC_STATE_DATA = field("state_data", SQLDataType.JSON)
    private val DOC_FILE_NAME = field("file_name", SQLDataType.VARCHAR(255))
    private val DOC_FILE_SIZE = field("file_size", SQLDataType.BIGINT)
    private val DOC_UPLOADED_AT = field("uploaded_at", SQLDataType.LOCALDATETIME(6))
    private val DOC_FILE_STORAGE_ID = field("file_storage_id", SQLDataType.VARCHAR(255))
    private val DOC_SCAN_REPORT = field("scan_report", SQLDataType.JSON)
    private val DOC_UPDATED_AT = field("updated_at", SQLDataType.LOCALDATETIME(6))
  }

  fun getOutbox(): JooqOutbox<String, DocumentEffect> = outbox

  override fun saveWithOutbox(
    value: DocumentUpload,
    outboxMessages: List<OutboxMessage<String, DocumentEffect>>
  ): Result<DocumentUpload> = runCatching {
    dsl.transactionResult { config ->
      val txDsl = DSL.using(config)

      upsertDocument(txDsl, value)
      outbox.insert(txDsl, outboxMessages)

      value
    }
  }

  fun findById(id: String): Result<DocumentUpload?> = runCatching {
    dsl.selectFrom(DOCUMENTS)
      .where(DOC_ID.eq(id))
      .fetchOne()
      ?.let { record ->
        DocumentUpload(
          id = record.get(DOC_ID)!!,
          state = JsonSerializer.deserializeState(
            record.get(DOC_STATE)!!,
            record.get(DOC_STATE_DATA)?.let { it.data() }
          ),
          fileName = record.get(DOC_FILE_NAME)!!,
          fileSize = record.get(DOC_FILE_SIZE)!!,
          uploadedAt = record.get(DOC_UPLOADED_AT)!!.toInstantSafe(),
          fileStorageId = record.get(DOC_FILE_STORAGE_ID),
          scanReport = record.get(DOC_SCAN_REPORT)?.let {
            JsonSerializer.deserializeScanReport(it.data())
          }
        )
      }
  }

  fun create(document: DocumentUpload): Result<DocumentUpload> = runCatching {
    insertDocument(dsl, document)
    document
  }

  fun updateWithFields(document: DocumentUpload): Result<DocumentUpload> = runCatching {
    upsertDocument(dsl, document)
    document
  }

  private fun insertDocument(ctx: DSLContext, doc: DocumentUpload) {
    val (stateName, stateData) = JsonSerializer.serializeState(doc.state)

    ctx.insertInto(DOCUMENTS)
      .set(DOC_ID, doc.id)
      .set(DOC_STATE, stateName)
      .set(DOC_STATE_DATA, stateData?.let { JSON.json(it) })
      .set(DOC_FILE_NAME, doc.fileName)
      .set(DOC_FILE_SIZE, doc.fileSize)
      .set(DOC_UPLOADED_AT, LocalDateTime.ofInstant(doc.uploadedAt, ZoneOffset.UTC))
      .set(DOC_FILE_STORAGE_ID, doc.fileStorageId)
      .set(DOC_SCAN_REPORT, JsonSerializer.serializeScanReport(doc.scanReport)?.let { JSON.json(it) })
      .execute()
  }

  private fun upsertDocument(ctx: DSLContext, doc: DocumentUpload) {
    val (stateName, stateData) = JsonSerializer.serializeState(doc.state)
    val now = LocalDateTime.now(ZoneOffset.UTC)

    ctx.insertInto(DOCUMENTS)
      .set(DOC_ID, doc.id)
      .set(DOC_STATE, stateName)
      .set(DOC_STATE_DATA, stateData?.let { JSON.json(it) })
      .set(DOC_FILE_NAME, doc.fileName)
      .set(DOC_FILE_SIZE, doc.fileSize)
      .set(DOC_UPLOADED_AT, LocalDateTime.ofInstant(doc.uploadedAt, ZoneOffset.UTC))
      .set(DOC_FILE_STORAGE_ID, doc.fileStorageId)
      .set(DOC_SCAN_REPORT, JsonSerializer.serializeScanReport(doc.scanReport)?.let { JSON.json(it) })
      .set(DOC_UPDATED_AT, now)
      .onDuplicateKeyUpdate()
      .set(DOC_STATE, stateName)
      .set(DOC_STATE_DATA, stateData?.let { JSON.json(it) })
      .set(DOC_FILE_STORAGE_ID, DSL.coalesce(DSL.value(doc.fileStorageId), DOC_FILE_STORAGE_ID))
      .set(DOC_SCAN_REPORT, DSL.coalesce(
        JsonSerializer.serializeScanReport(doc.scanReport)?.let { DSL.value(JSON.json(it)) },
        DOC_SCAN_REPORT
      ))
      .set(DOC_UPDATED_AT, now)
      .execute()
  }
}
