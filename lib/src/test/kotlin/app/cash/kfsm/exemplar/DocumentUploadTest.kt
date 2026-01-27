package app.cash.kfsm.exemplar

import app.cash.kfsm.Decision
import app.cash.kfsm.EffectOutcome
import app.cash.kfsm.OutboxMessage
import app.cash.kfsm.Repository
import app.cash.kfsm.StateMachine
import app.cash.kfsm.StateMachineUtilities
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DocumentUploadTest : StringSpec({
  isolationMode = IsolationMode.InstancePerTest

  // In-memory repository for testing
  val savedValues = mutableListOf<DocumentUpload>()
  val savedOutboxMessages = mutableListOf<OutboxMessage<String, DocumentEffect>>()

  val repository = object : Repository<String, DocumentUpload, DocumentState, DocumentEffect> {
    override fun saveWithOutbox(
      value: DocumentUpload,
      outboxMessages: List<OutboxMessage<String, DocumentEffect>>
    ): Result<DocumentUpload> {
      savedValues.add(value)
      savedOutboxMessages.addAll(outboxMessages)
      return Result.success(value)
    }
  }

  val stateMachine = StateMachine(repository)

  "transition: RequestUpload from Idle transitions to Uploading with UploadFile effect" {
    val doc = DocumentUpload(id = "doc-1", state = DocumentState.Idle)
    val transition = RequestUpload("test.pdf", "content".toByteArray())

    val decision = transition.decide(doc)

    decision.shouldBeInstanceOf<Decision.Accept<DocumentState, DocumentEffect>>()
    decision.state shouldBe DocumentState.Uploading
    decision.effects.size shouldBe 1
    decision.effects[0].shouldBeInstanceOf<DocumentEffect.UploadFile>()
  }

  "transition: UploadCompleted from Uploading transitions to Scanning with ScanFile effect" {
    val doc = DocumentUpload(id = "doc-1", state = DocumentState.Uploading, fileName = "test.pdf")
    val transition = UploadCompleted("file-123")

    val decision = transition.decide(doc)

    decision.shouldBeInstanceOf<Decision.Accept<DocumentState, DocumentEffect>>()
    decision.state shouldBe DocumentState.Scanning
    decision.effects.size shouldBe 1
    val scanEffect = decision.effects[0].shouldBeInstanceOf<DocumentEffect.ScanFile>()
    scanEffect.fileId shouldBe "file-123"
  }

  "transition: ScanSucceeded from Scanning transitions to Accepted with NotifyUser effect" {
    val doc = DocumentUpload(id = "doc-1", state = DocumentState.Scanning, fileId = "file-123")
    val report = ScanReport(checksum = "abc123", virusFree = true)
    val transition = ScanSucceeded(report)

    val decision = transition.decide(doc)

    decision.shouldBeInstanceOf<Decision.Accept<DocumentState, DocumentEffect>>()
    decision.state shouldBe DocumentState.Accepted
    decision.effects.size shouldBe 1
    decision.effects[0].shouldBeInstanceOf<DocumentEffect.NotifyUser>()
  }

  "transition: ScanFailed from Scanning transitions to Rejected with NotifyUser effect" {
    val doc = DocumentUpload(id = "doc-1", state = DocumentState.Scanning, fileId = "file-123")
    val transition = ScanFailed("Virus detected")

    val decision = transition.decide(doc)

    decision.shouldBeInstanceOf<Decision.Accept<DocumentState, DocumentEffect>>()
    decision.state.shouldBeInstanceOf<DocumentState.Rejected>()
    (decision.state as DocumentState.Rejected).reason shouldBe "Virus detected"
  }

  "state machine: apply transition persists value and outbox message" {
    val doc = DocumentUpload(id = "doc-1", state = DocumentState.Idle)
    val transition = RequestUpload("test.pdf", "content".toByteArray())

    val result = stateMachine.transition(doc, transition)

    result.shouldBeSuccess()
    result.getOrThrow().state shouldBe DocumentState.Uploading

    savedValues.size shouldBe 1
    savedValues[0].state shouldBe DocumentState.Uploading

    savedOutboxMessages.size shouldBe 1
    savedOutboxMessages[0].effect.shouldBeInstanceOf<DocumentEffect.UploadFile>()
  }

  "state machine: rejects transition from wrong state" {
    val doc = DocumentUpload(id = "doc-1", state = DocumentState.Idle)
    // Try to apply UploadCompleted when in Idle state (should be Uploading)
    val transition = UploadCompleted("file-123")

    val result = stateMachine.transition(doc, transition)

    result.shouldBeFailure()
    savedValues.size shouldBe 0
    savedOutboxMessages.size shouldBe 0
  }

  "state machine: idempotent when already at target state" {
    val doc = DocumentUpload(id = "doc-1", state = DocumentState.Uploading)
    // Try to apply transition to Uploading when already in Uploading
    val transition = RequestUpload("test.pdf", "content".toByteArray())

    val result = stateMachine.transition(doc, transition)

    // Should be a no-op success
    result.shouldBeSuccess()
    result.getOrThrow().state shouldBe DocumentState.Uploading
    savedValues.size shouldBe 0 // Not persisted because no change
  }

  "effect handler: upload file returns UploadCompleted transition" {
    var uploadedFileName: String? = null
    val handler = DocumentUploadEffectHandler(
      uploadFile = { fileName, _ ->
        uploadedFileName = fileName
        Result.success("generated-file-id")
      },
      scanFile = { Result.failure(NotImplementedError()) },
      notifyUser = { _, _ -> Result.success(Unit) }
    )

    val result = handler.handle("doc-1", DocumentEffect.UploadFile("test.pdf", "content".toByteArray()))

    uploadedFileName shouldBe "test.pdf"
    result.shouldBeSuccess()
    val outcome = result.getOrThrow()
    outcome.shouldBeInstanceOf<EffectOutcome.TransitionProduced<String, DocumentUpload, DocumentState, DocumentEffect>>()
    outcome.transition.shouldBeInstanceOf<UploadCompleted>()
  }

  "effect handler: scan file success returns ScanSucceeded transition" {
    val report = ScanReport("checksum", virusFree = true)
    val handler = DocumentUploadEffectHandler(
      uploadFile = { _, _ -> Result.failure(NotImplementedError()) },
      scanFile = { Result.success(DocumentUploadEffectHandler.ScanResult.Success(report)) },
      notifyUser = { _, _ -> Result.success(Unit) }
    )

    val result = handler.handle("doc-1", DocumentEffect.ScanFile("file-123"))

    result.shouldBeSuccess()
    val outcome = result.getOrThrow()
    outcome.shouldBeInstanceOf<EffectOutcome.TransitionProduced<String, DocumentUpload, DocumentState, DocumentEffect>>()
    outcome.transition.shouldBeInstanceOf<ScanSucceeded>()
  }

  "effect handler: scan file failure returns ScanFailed transition" {
    val handler = DocumentUploadEffectHandler(
      uploadFile = { _, _ -> Result.failure(NotImplementedError()) },
      scanFile = { Result.success(DocumentUploadEffectHandler.ScanResult.Failure("Virus detected")) },
      notifyUser = { _, _ -> Result.success(Unit) }
    )

    val result = handler.handle("doc-1", DocumentEffect.ScanFile("file-123"))

    result.shouldBeSuccess()
    val outcome = result.getOrThrow()
    outcome.shouldBeInstanceOf<EffectOutcome.TransitionProduced<String, DocumentUpload, DocumentState, DocumentEffect>>()
    val transition = outcome.transition
    transition.shouldBeInstanceOf<ScanFailed>()
  }

  "effect handler: notify user returns Completed outcome" {
    var notifiedDocId: String? = null
    var notifiedStatus: String? = null
    val handler = DocumentUploadEffectHandler(
      uploadFile = { _, _ -> Result.failure(NotImplementedError()) },
      scanFile = { Result.failure(NotImplementedError()) },
      notifyUser = { docId, status ->
        notifiedDocId = docId
        notifiedStatus = status
        Result.success(Unit)
      }
    )

    val result = handler.handle("doc-1", DocumentEffect.NotifyUser("doc-1", "Upload complete"))

    result.shouldBeSuccess()
    val outcome = result.getOrThrow()
    outcome shouldBe EffectOutcome.Completed
    notifiedDocId shouldBe "doc-1"
    notifiedStatus shouldBe "Upload complete"
  }

  "state machine utilities: mermaid diagram shows static transitions" {
    StateMachineUtilities.mermaid(DocumentState.Idle).shouldBeSuccess(
      """
      stateDiagram-v2
      [*] --> Idle
      Idle --> Uploading
      Scanning --> Accepted
      Uploading --> Scanning
      """.trimIndent()
    )
  }
})
