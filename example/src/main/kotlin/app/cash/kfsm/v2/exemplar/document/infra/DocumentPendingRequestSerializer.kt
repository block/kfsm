package app.cash.kfsm.v2.exemplar.document.infra

import app.cash.kfsm.v2.exemplar.document.DocumentUpload
import app.cash.kfsm.v2.jooq.PendingRequestSerializer
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant

/**
 * Pending request serializer for DocumentUpload using Moshi.
 */
object DocumentPendingRequestSerializer {

  private object InstantAdapter {
    @ToJson fun toJson(instant: Instant): String = instant.toString()
    @FromJson fun fromJson(value: String): Instant = Instant.parse(value)
  }

  private val moshi: Moshi = Moshi.Builder()
    .add(InstantAdapter)
    .addLast(KotlinJsonAdapterFactory())
    .build()

  /**
   * Intermediate representation for serializing DocumentUpload.
   */
  @JsonClass(generateAdapter = false)
  private data class SerializedDocumentUpload(
    val id: String,
    val stateName: String,
    val stateData: String?,
    val fileName: String,
    val fileSize: Long,
    val uploadedAt: String,
    val fileStorageId: String?,
    val scanReport: String?
  )

  private val adapter = moshi.adapter(SerializedDocumentUpload::class.java)

  val instance: PendingRequestSerializer<String, DocumentUpload> = object : PendingRequestSerializer<String, DocumentUpload> {
    override fun serializeId(id: String): String = id
    override fun deserializeId(serialized: String): String = serialized

    override fun serializeValue(value: DocumentUpload): String {
      val (stateName, stateData) = JsonSerializer.serializeState(value.state)
      return adapter.toJson(
        SerializedDocumentUpload(
          id = value.id,
          stateName = stateName,
          stateData = stateData,
          fileName = value.fileName,
          fileSize = value.fileSize,
          uploadedAt = value.uploadedAt.toString(),
          fileStorageId = value.fileStorageId,
          scanReport = JsonSerializer.serializeScanReport(value.scanReport)
        )
      )
    }

    override fun deserializeValue(serialized: String): DocumentUpload {
      val dto = adapter.fromJson(serialized)!!
      return DocumentUpload(
        id = dto.id,
        state = JsonSerializer.deserializeState(dto.stateName, dto.stateData),
        fileName = dto.fileName,
        fileSize = dto.fileSize,
        uploadedAt = Instant.parse(dto.uploadedAt),
        fileStorageId = dto.fileStorageId,
        scanReport = dto.scanReport?.let { JsonSerializer.deserializeScanReport(it) }
      )
    }
  }
}
