package app.cash.kfsm.v2.exemplar.document

import app.cash.kfsm.v2.Decision
import app.cash.kfsm.v2.Transition

/**
 * Base class for document upload transitions.
 */
abstract class DocumentTransition(
  from: Set<DocumentState>,
  to: DocumentState
) : Transition<String, DocumentUpload, DocumentState, DocumentEffect>(from, to) {
  constructor(from: DocumentState, to: DocumentState) : this(setOf(from), to)
}

// --- Transitions ---

/**
 * Start uploading a file from Created state.
 */
class StartUpload(
  private val content: ByteArray
) : DocumentTransition(from = DocumentState.Created, to = DocumentState.Uploading) {

  override fun decide(value: DocumentUpload): Decision<DocumentUpload, DocumentState, DocumentEffect> =
    Decision.accept(
      value = value.update(DocumentState.Uploading),
      effects = listOf(
        DocumentEffect.UploadToStorage(
          documentId = value.id,
          fileName = value.fileName,
          content = content
        )
      )
    )

  override fun equals(other: Any?) = other is StartUpload && content.contentEquals(other.content)
  override fun hashCode() = content.contentHashCode()
}

/**
 * Mark upload as completed and request virus scan.
 *
 * This transition demonstrates updating value fields beyond just the state:
 * it stores the fileStorageId returned from the upload effect.
 */
class UploadCompleted(
  private val fileStorageId: String
) : DocumentTransition(from = DocumentState.Uploading, to = DocumentState.AwaitingScan) {

  override fun decide(value: DocumentUpload): Decision<DocumentUpload, DocumentState, DocumentEffect> =
    Decision.accept(
      value = value.withFileStorageId(fileStorageId).update(DocumentState.AwaitingScan),
      effects = listOf(
        DocumentEffect.RequestVirusScan(
          documentId = value.id,
          fileStorageId = fileStorageId
        )
      )
    )
}

/**
 * Upload failed.
 */
class UploadFailed(
  private val reason: String
) : DocumentTransition(from = DocumentState.Uploading, to = DocumentState.Failed(reason)) {

  override fun decide(value: DocumentUpload): Decision<DocumentUpload, DocumentState, DocumentEffect> =
    Decision.accept(
      value = value.update(DocumentState.Failed(reason)),
      effects = listOf(
        DocumentEffect.NotifyUser(value.id, "Upload failed: $reason")
      )
    )
}

/**
 * Virus scan has started.
 */
class ScanStarted : DocumentTransition(from = DocumentState.AwaitingScan, to = DocumentState.Scanning) {

  override fun decide(value: DocumentUpload): Decision<DocumentUpload, DocumentState, DocumentEffect> =
    Decision.accept(
      value = value.update(DocumentState.Scanning)
    )
}

/**
 * Virus scan completed - file is clean.
 *
 * This transition stores the scan report on the value.
 */
class ScanPassed(
  private val report: ScanReport
) : DocumentTransition(from = DocumentState.Scanning, to = DocumentState.Accepted) {

  override fun decide(value: DocumentUpload): Decision<DocumentUpload, DocumentState, DocumentEffect> =
    Decision.accept(
      value = value.withScanReport(report).update(DocumentState.Accepted),
      effects = listOf(
        DocumentEffect.NotifyUser(value.id, "Document accepted: ${value.fileName}")
      )
    )
}

/**
 * Virus scan completed - threat detected.
 */
class ScanFailed(
  private val report: ScanReport
) : DocumentTransition(from = DocumentState.Scanning, to = DocumentState.Quarantined(report.threatName ?: "Unknown threat")) {

  override fun decide(value: DocumentUpload): Decision<DocumentUpload, DocumentState, DocumentEffect> =
    Decision.accept(
      value = value.withScanReport(report).update(DocumentState.Quarantined(report.threatName ?: "Unknown threat")),
      effects = listOfNotNull(
        value.fileStorageId?.let { DocumentEffect.DeleteFromStorage(it) },
        DocumentEffect.NotifyUser(
          value.id,
          "Document quarantined: ${report.threatName ?: "threat detected"}"
        )
      )
    )
}

/**
 * An error occurred during processing.
 * Can be applied from Uploading, AwaitingScan, or Scanning states.
 */
class ErrorOccurred(
  private val reason: String
) : DocumentTransition(
  from = setOf(DocumentState.Uploading, DocumentState.AwaitingScan, DocumentState.Scanning),
  to = DocumentState.Failed(reason)
) {

  override fun decide(value: DocumentUpload): Decision<DocumentUpload, DocumentState, DocumentEffect> =
    Decision.accept(
      value = value.update(DocumentState.Failed(reason)),
      effects = listOfNotNull(
        value.fileStorageId?.let { DocumentEffect.DeleteFromStorage(it) },
        DocumentEffect.NotifyUser(value.id, "Error: $reason")
      )
    )
}
