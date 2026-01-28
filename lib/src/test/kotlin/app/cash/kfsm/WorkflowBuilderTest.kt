package app.cash.kfsm

import app.cash.kfsm.exemplar.DocumentEffect
import app.cash.kfsm.exemplar.DocumentState
import app.cash.kfsm.exemplar.DocumentUpload
import app.cash.kfsm.exemplar.RequestUpload
import app.cash.kfsm.exemplar.ScanReport
import app.cash.kfsm.exemplar.ScanSucceeded
import app.cash.kfsm.exemplar.UploadCompleted
import app.cash.kfsm.testing.InMemoryOutbox
import app.cash.kfsm.testing.InMemoryPendingRequestStore
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class WorkflowBuilderTest : StringSpec({
    isolationMode = IsolationMode.InstancePerTest

    "workflow builder creates working state machine" {
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

        val outbox = InMemoryOutbox<String, DocumentEffect>()
        val pendingStore = InMemoryPendingRequestStore<String, DocumentUpload>()
        val values = mutableMapOf<String, DocumentUpload>()

        val workflow = workflow<String, DocumentUpload, DocumentState, DocumentEffect> {
            repository(repository)
            valueLoader { id -> Result.success(values[id]) }
            outbox(outbox)
            pendingRequestStore(pendingStore)

            settledWhen { state ->
                state is DocumentState.Accepted || state is DocumentState.Rejected
            }

            effects {
                on<DocumentEffect.UploadFile> { id, effect ->
                    EffectOutcome.TransitionProduced(id, UploadCompleted("file-${effect.fileName}"))
                }
                on<DocumentEffect.ScanFile> { id, _ ->
                    EffectOutcome.TransitionProduced(id, ScanSucceeded(ScanReport("checksum", true)))
                }
                on<DocumentEffect.NotifyUser> { _, _ ->
                    EffectOutcome.Completed
                }
            }
        }

        val doc = DocumentUpload(id = "doc-1", state = DocumentState.Idle)
        values["doc-1"] = doc

        val result = workflow.stateMachine.transition(doc, RequestUpload("test.pdf", "content".toByteArray()))

        result.shouldBeSuccess()
        result.getOrThrow().state shouldBe DocumentState.Uploading
        savedValues.size shouldBe 1
        savedOutboxMessages.size shouldBe 1
        savedOutboxMessages[0].effect.shouldBeInstanceOf<DocumentEffect.UploadFile>()
    }

    "workflow builder with settledStates convenience method" {
        val repository = object : Repository<String, DocumentUpload, DocumentState, DocumentEffect> {
            override fun saveWithOutbox(
                value: DocumentUpload,
                outboxMessages: List<OutboxMessage<String, DocumentEffect>>
            ): Result<DocumentUpload> = Result.success(value)
        }

        val workflow = workflow<String, DocumentUpload, DocumentState, DocumentEffect> {
            repository(repository)
            valueLoader { Result.success(null) }
            outbox(InMemoryOutbox())
            pendingRequestStore(InMemoryPendingRequestStore())
            settledStates(DocumentState.Accepted)

            effects {
                on<DocumentEffect.UploadFile> { id, _ ->
                    EffectOutcome.TransitionProduced(id, UploadCompleted("file-id"))
                }
                on<DocumentEffect.ScanFile> { id, _ ->
                    EffectOutcome.TransitionProduced(id, ScanSucceeded(ScanReport("checksum", true)))
                }
                on<DocumentEffect.NotifyUser> { _, _ ->
                    EffectOutcome.Completed
                }
            }
        }

        val doc = DocumentUpload(id = "doc-1", state = DocumentState.Idle)
        val result = workflow.stateMachine.transition(doc, RequestUpload("test.pdf", "content".toByteArray()))

        result.shouldBeSuccess()
    }

    "effect handler builder routes to correct handler" {
        val handledEffects = mutableListOf<String>()

        val handler = EffectHandlerBuilder<String, DocumentUpload, DocumentState, DocumentEffect>().apply {
            on<DocumentEffect.UploadFile> { id, _ ->
                handledEffects.add("upload")
                EffectOutcome.TransitionProduced(id, UploadCompleted("file-id"))
            }
            on<DocumentEffect.ScanFile> { id, _ ->
                handledEffects.add("scan")
                EffectOutcome.TransitionProduced(id, ScanSucceeded(ScanReport("checksum", true)))
            }
            on<DocumentEffect.NotifyUser> { _, _ ->
                handledEffects.add("notify")
                EffectOutcome.Completed
            }
        }.build()

        handler.handle("doc-1", DocumentEffect.UploadFile("test.pdf", byteArrayOf()))
        handler.handle("doc-1", DocumentEffect.ScanFile("file-id"))
        handler.handle("doc-1", DocumentEffect.NotifyUser("doc-1", "Done"))

        handledEffects shouldBe listOf("upload", "scan", "notify")
    }

    "effect handler builder catches exceptions in handlers" {
        val handler = EffectHandlerBuilder<String, DocumentUpload, DocumentState, DocumentEffect>().apply {
            on<DocumentEffect.UploadFile> { _, _ ->
                throw RuntimeException("Upload service unavailable")
            }
            on<DocumentEffect.ScanFile> { id, _ ->
                EffectOutcome.TransitionProduced(id, ScanSucceeded(ScanReport("checksum", true)))
            }
            on<DocumentEffect.NotifyUser> { _, _ ->
                EffectOutcome.Completed
            }
        }.build()

        val result = handler.handle("doc-1", DocumentEffect.UploadFile("test.pdf", byteArrayOf()))

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldBe "Upload service unavailable"
    }
})
