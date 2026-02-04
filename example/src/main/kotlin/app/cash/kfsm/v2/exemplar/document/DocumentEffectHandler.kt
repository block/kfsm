package app.cash.kfsm.v2.exemplar.document

import app.cash.kfsm.v2.exemplar.document.infra.MockFileStorage
import app.cash.kfsm.v2.exemplar.document.infra.MockVirusScanner
import app.cash.kfsm.v2.EffectHandler
import app.cash.kfsm.v2.EffectOutcome
import java.util.concurrent.ConcurrentHashMap

/**
 * Effect handler that executes document workflow effects.
 *
 * Integrates with:
 * - File storage service for uploads/deletions
 * - Virus scanner service for file scanning
 * - Notification service for user notifications
 *
 * This handler demonstrates how effects are executed and produce
 * follow-up transitions that continue the workflow.
 */
class DocumentEffectHandler(
  private val fileStorage: MockFileStorage,
  private val virusScanner: MockVirusScanner,
  private val notificationSink: NotificationSink,
  private val repository: DocumentRepository
) : EffectHandler<String, DocumentUpload, DocumentState, DocumentEffect> {

  private val pendingScans = ConcurrentHashMap<String, String>()

  fun interface NotificationSink {
    fun send(documentId: String, message: String): Result<Unit>
  }

  interface DocumentRepository {
    fun findById(id: String): Result<DocumentUpload?>
    fun updateWithFields(document: DocumentUpload): Result<DocumentUpload>
  }

  override fun handle(
    valueId: String,
    effect: DocumentEffect
  ): Result<EffectOutcome<String, DocumentUpload, DocumentState, DocumentEffect>> = when (effect) {
    is DocumentEffect.UploadToStorage -> handleUpload(valueId, effect)
    is DocumentEffect.RequestVirusScan -> handleScanRequest(valueId, effect)
    is DocumentEffect.NotifyUser -> handleNotification(effect)
    is DocumentEffect.DeleteFromStorage -> handleDelete(effect)
  }

  private fun handleUpload(
    documentId: String,
    effect: DocumentEffect.UploadToStorage
  ): Result<EffectOutcome<String, DocumentUpload, DocumentState, DocumentEffect>> =
    fileStorage.upload(effect.fileName, effect.content)
      .flatMap { storageId ->
        repository.findById(documentId)
          .flatMap { doc ->
            if (doc != null) {
              repository.updateWithFields(doc.withFileStorageId(storageId))
            } else {
              Result.success(null)
            }
          }
          .map { storageId }
      }
      .map { storageId ->
        EffectOutcome.TransitionProduced(documentId, UploadCompleted(storageId))
      }
      .recoverCatching { error ->
        EffectOutcome.TransitionProduced(
          documentId,
          UploadFailed(error.message ?: "Upload failed")
        )
      }

  private fun handleScanRequest(
    documentId: String,
    effect: DocumentEffect.RequestVirusScan
  ): Result<EffectOutcome<String, DocumentUpload, DocumentState, DocumentEffect>> {
    pendingScans[effect.fileStorageId] = documentId
    return virusScanner.submitScan(documentId, effect.fileStorageId)
      .map {
        EffectOutcome.TransitionProduced(documentId, ScanStarted())
      }
  }

  private fun handleNotification(
    effect: DocumentEffect.NotifyUser
  ): Result<EffectOutcome<String, DocumentUpload, DocumentState, DocumentEffect>> =
    notificationSink.send(effect.documentId, effect.message)
      .map { EffectOutcome.Completed }

  private fun handleDelete(
    effect: DocumentEffect.DeleteFromStorage
  ): Result<EffectOutcome<String, DocumentUpload, DocumentState, DocumentEffect>> =
    fileStorage.delete(effect.fileStorageId)
      .map { EffectOutcome.Completed }
}

/**
 * Extension function to flatMap a Result.
 */
private fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
  fold(
    onSuccess = { transform(it) },
    onFailure = { Result.failure(it) }
  )
