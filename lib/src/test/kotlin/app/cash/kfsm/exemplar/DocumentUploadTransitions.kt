package app.cash.kfsm.exemplar

import app.cash.kfsm.Decision
import app.cash.kfsm.Transition

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
 * Request to upload a document.
 */
class RequestUpload(
  private val fileName: String,
  private val fileContent: ByteArray
) : DocumentTransition(from = DocumentState.Idle, to = DocumentState.Uploading) {

  override fun decide(value: DocumentUpload): Decision<DocumentState, DocumentEffect> =
    Decision.accept(
      state = DocumentState.Uploading,
      effects = listOf(DocumentEffect.UploadFile(fileName, fileContent))
    )
}

/**
 * Mark upload as completed and trigger virus scan.
 */
class UploadCompleted(
  private val fileId: String
) : DocumentTransition(from = DocumentState.Uploading, to = DocumentState.Scanning) {

  override fun decide(value: DocumentUpload): Decision<DocumentState, DocumentEffect> =
    Decision.accept(
      state = DocumentState.Scanning,
      effects = listOf(DocumentEffect.ScanFile(fileId))
    )
}

/**
 * Upload failed.
 */
class UploadFailed(
  private val reason: String
) : DocumentTransition(from = DocumentState.Uploading, to = DocumentState.Rejected(reason)) {

  override fun decide(value: DocumentUpload): Decision<DocumentState, DocumentEffect> =
    Decision.accept(
      state = DocumentState.Rejected(reason),
      effects = listOf(DocumentEffect.NotifyUser(value.id, "Upload failed: $reason"))
    )
}

/**
 * Virus scan succeeded - document is accepted.
 */
class ScanSucceeded(
  private val report: ScanReport
) : DocumentTransition(from = DocumentState.Scanning, to = DocumentState.Accepted) {

  override fun decide(value: DocumentUpload): Decision<DocumentState, DocumentEffect> =
    Decision.accept(
      state = DocumentState.Accepted,
      effects = listOf(DocumentEffect.NotifyUser(value.id, "Document accepted"))
    )
}

/**
 * Virus scan failed - document is rejected.
 */
class ScanFailed(
  private val reason: String
) : DocumentTransition(from = DocumentState.Scanning, to = DocumentState.Rejected(reason)) {

  override fun decide(value: DocumentUpload): Decision<DocumentState, DocumentEffect> =
    Decision.accept(
      state = DocumentState.Rejected(reason),
      effects = listOf(DocumentEffect.NotifyUser(value.id, "Document rejected: $reason"))
    )
}
