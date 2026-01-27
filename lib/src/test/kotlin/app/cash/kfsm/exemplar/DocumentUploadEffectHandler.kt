package app.cash.kfsm.exemplar

import app.cash.kfsm.EffectHandler
import app.cash.kfsm.EffectOutcome

/**
 * Executes effects and returns transitions to continue the workflow.
 *
 * In a real application, this would call external services (file storage, virus scanner, etc.).
 * For testing, we use pluggable lambdas to control the behavior.
 */
class DocumentUploadEffectHandler(
  private val uploadFile: (fileName: String, content: ByteArray) -> Result<String>,
  private val scanFile: (fileId: String) -> Result<ScanResult>,
  private val notifyUser: (documentId: String, status: String) -> Result<Unit>
) : EffectHandler<String, DocumentUpload, DocumentState, DocumentEffect> {

  sealed class ScanResult {
    data class Success(val report: ScanReport) : ScanResult()
    data class Failure(val reason: String) : ScanResult()
  }

  override fun handle(
    valueId: String,
    effect: DocumentEffect
  ): Result<EffectOutcome<String, DocumentUpload, DocumentState, DocumentEffect>> = when (effect) {
    is DocumentEffect.UploadFile ->
      uploadFile(effect.fileName, effect.content)
        .map { fileId ->
          EffectOutcome.TransitionProduced(valueId, UploadCompleted(fileId))
        }
        .recoverCatching { error ->
          EffectOutcome.TransitionProduced(valueId, UploadFailed(error.message ?: "Upload failed"))
        }

    is DocumentEffect.ScanFile ->
      scanFile(effect.fileId)
        .map { result ->
          val transition = when (result) {
            is ScanResult.Success -> ScanSucceeded(result.report)
            is ScanResult.Failure -> ScanFailed(result.reason)
          }
          EffectOutcome.TransitionProduced(valueId, transition)
        }

    is DocumentEffect.NotifyUser ->
      notifyUser(effect.documentId, effect.status)
        .map { EffectOutcome.Completed }
  }
}
