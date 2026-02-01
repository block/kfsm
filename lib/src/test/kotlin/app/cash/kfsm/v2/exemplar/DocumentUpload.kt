package app.cash.kfsm.v2.exemplar

import app.cash.kfsm.v2.Effect
import app.cash.kfsm.v2.State
import app.cash.kfsm.v2.Value

/**
 * Value representing a document upload workflow.
 */
data class DocumentUpload(
  override val id: String,
  override val state: DocumentState,
  val fileName: String? = null,
  val fileId: String? = null,
  val scanReport: ScanReport? = null,
  val rejectionReason: String? = null
) : Value<String, DocumentUpload, DocumentState> {
  override fun update(newState: DocumentState): DocumentUpload = copy(state = newState)
}

data class ScanReport(val checksum: String, val virusFree: Boolean)

// --- States ---

sealed class DocumentState : State<DocumentState>() {
  data object Idle : DocumentState() {
    override fun transitions() = setOf(Uploading)
  }

  data object Uploading : DocumentState() {
    override fun transitions(): Set<DocumentState> = setOf(Scanning)
    override fun canTransitionTo(other: DocumentState) = 
      super.canTransitionTo(other) || other is Rejected
  }

  data object Scanning : DocumentState() {
    override fun transitions(): Set<DocumentState> = setOf(Accepted)
    override fun canTransitionTo(other: DocumentState) = 
      super.canTransitionTo(other) || other is Rejected
  }

  data object Accepted : DocumentState() {
    override fun transitions(): Set<DocumentState> = emptySet()
  }

  data class Rejected(val reason: String) : DocumentState() {
    override fun transitions(): Set<DocumentState> = emptySet()
  }
}

// --- Effects ---

sealed class DocumentEffect : Effect {
  data class UploadFile(val fileName: String, val content: ByteArray) : DocumentEffect()
  data class ScanFile(val fileId: String) : DocumentEffect()
  data class NotifyUser(val documentId: String, val status: String) : DocumentEffect()
}
