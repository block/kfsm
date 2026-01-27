package app.cash.kfsm.exemplar.document

import app.cash.kfsm.Effect
import app.cash.kfsm.Invariant
import app.cash.kfsm.State
import app.cash.kfsm.Value
import app.cash.kfsm.invariant
import java.time.Instant

/**
 * A document upload that goes through virus scanning before being accepted.
 */
data class DocumentUpload(
  override val id: String,
  override val state: DocumentState,
  val fileName: String,
  val fileSize: Long,
  val uploadedAt: Instant = Instant.now(),
  val fileStorageId: String? = null,
  val scanReport: ScanReport? = null
) : Value<String, DocumentUpload, DocumentState> {
  override fun update(newState: DocumentState): DocumentUpload = copy(state = newState)

  fun withFileStorageId(storageId: String): DocumentUpload = copy(fileStorageId = storageId)
  fun withScanReport(report: ScanReport): DocumentUpload = copy(scanReport = report)
}

data class ScanReport(
  val scannedAt: Instant,
  val virusFree: Boolean,
  val checksum: String,
  val threatName: String? = null
)

// --- States ---

sealed class DocumentState : State<DocumentState>() {

  /** Initial state - document metadata created but file not yet uploaded to storage */
  data object Created : DocumentState() {
    override fun transitions() = setOf(Uploading)
  }

  /** File is being uploaded to storage */
  data object Uploading : DocumentState() {
    override fun transitions() = setOf(AwaitingScan)
    override fun canTransitionTo(other: DocumentState) =
      super.canTransitionTo(other) || other is Failed
  }

  /** File uploaded, waiting for virus scan to complete */
  data object AwaitingScan : DocumentState() {
    override fun transitions() = setOf(Scanning)
    override fun canTransitionTo(other: DocumentState) =
      super.canTransitionTo(other) || other is Failed

    // Invariant: file must have been stored before we can request a scan
    @Suppress("UNCHECKED_CAST")
    override fun <V> invariants(): List<Invariant<V>> = listOf(
      invariant<Any?>("File storage ID must be present when awaiting scan") { value ->
        (value as? DocumentUpload)?.fileStorageId != null
      } as Invariant<V>
    )
  }

  /** Virus scan in progress */
  data object Scanning : DocumentState() {
    override fun transitions() = setOf(Accepted)
    override fun canTransitionTo(other: DocumentState) =
      super.canTransitionTo(other) || other is Quarantined || other is Failed
  }

  /** Document passed virus scan and is accepted */
  data object Accepted : DocumentState() {
    override fun transitions(): Set<DocumentState> = emptySet()

    // Invariant: accepted documents must have a clean scan report
    @Suppress("UNCHECKED_CAST")
    override fun <V> invariants(): List<Invariant<V>> = listOf(
      invariant<Any?>("Accepted documents must have a scan report") { value ->
        (value as? DocumentUpload)?.scanReport != null
      } as Invariant<V>,
      invariant<Any?>("Accepted documents must have passed virus scan") { value ->
        (value as? DocumentUpload)?.scanReport?.virusFree == true
      } as Invariant<V>
    )
  }

  /** Document failed virus scan and is quarantined */
  data class Quarantined(val reason: String) : DocumentState() {
    override fun transitions(): Set<DocumentState> = emptySet()
  }

  /** Processing failed due to an error */
  data class Failed(val reason: String) : DocumentState() {
    override fun transitions(): Set<DocumentState> = emptySet()
  }
}

// --- Effects ---

sealed class DocumentEffect : Effect {
  /** Upload file content to storage service */
  data class UploadToStorage(
    val documentId: String,
    val fileName: String,
    val content: ByteArray
  ) : DocumentEffect() {
    override fun equals(other: Any?) =
      other is UploadToStorage && documentId == other.documentId && fileName == other.fileName
    override fun hashCode() = 31 * documentId.hashCode() + fileName.hashCode()
  }

  /** Request virus scan from scanning service */
  data class RequestVirusScan(
    val documentId: String,
    val fileStorageId: String
  ) : DocumentEffect()

  /** Notify user about document status */
  data class NotifyUser(
    val documentId: String,
    val message: String
  ) : DocumentEffect()

  /** Delete file from storage (e.g., when quarantined) */
  data class DeleteFromStorage(
    val fileStorageId: String
  ) : DocumentEffect()
}
