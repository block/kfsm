package app.cash.kfsm.exemplar.document.infra

import app.cash.kfsm.exemplar.document.DocumentEffect
import app.cash.kfsm.exemplar.document.DocumentState
import app.cash.kfsm.exemplar.document.ScanReport
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * JSON serialization for persisting states and effects to the database.
 */
object JsonSerializer {
  private val simpleMapper: ObjectMapper = ObjectMapper()
    .registerModule(KotlinModule.Builder().build())
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

  private val polymorphicMapper: ObjectMapper = ObjectMapper()
    .registerModule(KotlinModule.Builder().build())
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .activateDefaultTyping(
      BasicPolymorphicTypeValidator.builder()
        .allowIfBaseType(DocumentEffect::class.java)
        .allowIfBaseType(DocumentState::class.java)
        .allowIfBaseType(ScanReport::class.java)
        .allowIfSubType(Any::class.java)
        .build(),
      ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE,
      JsonTypeInfo.As.PROPERTY
    )

  fun serializeEffect(effect: DocumentEffect): String =
    polymorphicMapper.writerFor(DocumentEffect::class.java).writeValueAsString(effect)

  fun deserializeEffect(json: String): DocumentEffect =
    polymorphicMapper.readerFor(DocumentEffect::class.java).readValue(json)

  fun serializeState(state: DocumentState): Pair<String, String?> {
    val stateName = when (state) {
      is DocumentState.Created -> "Created"
      is DocumentState.Uploading -> "Uploading"
      is DocumentState.AwaitingScan -> "AwaitingScan"
      is DocumentState.Scanning -> "Scanning"
      is DocumentState.Accepted -> "Accepted"
      is DocumentState.Quarantined -> "Quarantined"
      is DocumentState.Failed -> "Failed"
    }
    val stateData = when (state) {
      is DocumentState.Quarantined -> simpleMapper.writeValueAsString(mapOf("reason" to state.reason))
      is DocumentState.Failed -> simpleMapper.writeValueAsString(mapOf("reason" to state.reason))
      else -> null
    }
    return stateName to stateData
  }

  fun deserializeState(stateName: String, stateData: String?): DocumentState = when (stateName) {
    "Created" -> DocumentState.Created
    "Uploading" -> DocumentState.Uploading
    "AwaitingScan" -> DocumentState.AwaitingScan
    "Scanning" -> DocumentState.Scanning
    "Accepted" -> DocumentState.Accepted
    "Quarantined" -> {
      val data = stateData?.let { simpleMapper.readValue<Map<String, String>>(it) }
      DocumentState.Quarantined(data?.get("reason") ?: "Unknown")
    }
    "Failed" -> {
      val data = stateData?.let { simpleMapper.readValue<Map<String, String>>(it) }
      DocumentState.Failed(data?.get("reason") ?: "Unknown")
    }
    else -> throw IllegalArgumentException("Unknown state: $stateName")
  }

  fun serializeScanReport(report: ScanReport?): String? =
    report?.let { simpleMapper.writeValueAsString(it) }

  fun deserializeScanReport(json: String?): ScanReport? =
    json?.let { simpleMapper.readValue(it) }
}
