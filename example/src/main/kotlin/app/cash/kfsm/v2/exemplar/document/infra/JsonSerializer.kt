package app.cash.kfsm.v2.exemplar.document.infra

import app.cash.kfsm.v2.exemplar.document.DocumentState
import app.cash.kfsm.v2.exemplar.document.ScanReport
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant

/**
 * JSON serialization for persisting states and effects to the database.
 */
object JsonSerializer {

  private object InstantAdapter {
    @ToJson fun toJson(instant: Instant): String = instant.toString()
    @FromJson fun fromJson(value: String): Instant = Instant.parse(value)
  }

  private val moshi: Moshi = Moshi.Builder()
    .add(InstantAdapter)
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val mapAdapter = moshi.adapter<Map<String, String>>(
    Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
  )

  private val scanReportAdapter = moshi.adapter(ScanReport::class.java)

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
      is DocumentState.Quarantined -> mapAdapter.toJson(mapOf("reason" to state.reason))
      is DocumentState.Failed -> mapAdapter.toJson(mapOf("reason" to state.reason))
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
      val data = stateData?.let { mapAdapter.fromJson(it) }
      DocumentState.Quarantined(data?.get("reason") ?: "Unknown")
    }
    "Failed" -> {
      val data = stateData?.let { mapAdapter.fromJson(it) }
      DocumentState.Failed(data?.get("reason") ?: "Unknown")
    }
    else -> throw IllegalArgumentException("Unknown state: $stateName")
  }

  fun serializeScanReport(report: ScanReport?): String? =
    report?.let { scanReportAdapter.toJson(it) }

  fun deserializeScanReport(json: String?): ScanReport? =
    json?.let { scanReportAdapter.fromJson(it) }
}
