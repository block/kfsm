package app.cash.kfsm.v2.exemplar

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
 * Request to upload a document.
 *
 * Demonstrates Decision.reject: files larger than maxFileSize are rejected
 * without transitioning to a new state.
 */
class RequestUpload(
  private val fileName: String,
  private val fileContent: ByteArray,
  private val maxFileSize: Long = 10 * 1024 * 1024 // 10MB default
) : DocumentTransition(from = DocumentState.Idle, to = DocumentState.Uploading) {

  override fun decide(value: DocumentUpload): Decision<DocumentUpload, DocumentState, DocumentEffect> =
    if (fileContent.size > maxFileSize) {
      Decision.reject("File size ${fileContent.size} exceeds maximum allowed size of $maxFileSize bytes")
    } else {
      Decision.accept(
        value = value.update(DocumentState.Uploading),
        effects = listOf(DocumentEffect.UploadFile(fileName, fileContent))
      )
    }
}

/**
 * Mark upload as completed and trigger virus scan.
 */
class UploadCompleted(
  private val fileId: String
) : DocumentTransition(from = DocumentState.Uploading, to = DocumentState.Scanning) {

  override fun decide(value: DocumentUpload): Decision<DocumentUpload, DocumentState, DocumentEffect> =
    Decision.accept(
      value = value.update(DocumentState.Scanning),
      effects = listOf(DocumentEffect.ScanFile(fileId))
    )
}

/**
 * Upload failed.
 */
class UploadFailed(
  private val reason: String
) : DocumentTransition(from = DocumentState.Uploading, to = DocumentState.Rejected(reason)) {

  override fun decide(value: DocumentUpload): Decision<DocumentUpload, DocumentState, DocumentEffect> =
    Decision.accept(
      value = value.update(DocumentState.Rejected(reason)),
      effects = listOf(DocumentEffect.NotifyUser(value.id, "Upload failed: $reason"))
    )
}

/**
 * Virus scan succeeded - document is accepted.
 */
class ScanSucceeded(
  private val report: ScanReport
) : DocumentTransition(from = DocumentState.Scanning, to = DocumentState.Accepted) {

  override fun decide(value: DocumentUpload): Decision<DocumentUpload, DocumentState, DocumentEffect> =
    Decision.accept(
      value = value.update(DocumentState.Accepted),
      effects = listOf(DocumentEffect.NotifyUser(value.id, "Document accepted"))
    )
}

/**
 * Virus scan failed - document is rejected.
 */
class ScanFailed(
  private val reason: String
) : DocumentTransition(from = DocumentState.Scanning, to = DocumentState.Rejected(reason)) {

  override fun decide(value: DocumentUpload): Decision<DocumentUpload, DocumentState, DocumentEffect> =
    Decision.accept(
      value = value.update(DocumentState.Rejected(reason)),
      effects = listOf(DocumentEffect.NotifyUser(value.id, "Document rejected: $reason"))
    )
}
