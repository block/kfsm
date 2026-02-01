package app.cash.kfsm.v2

import app.cash.kfsm.v2.exemplar.DocumentEffect
import app.cash.kfsm.v2.exemplar.DocumentState
import app.cash.kfsm.v2.exemplar.DocumentUpload
import app.cash.kfsm.v2.exemplar.RequestUpload
import app.cash.kfsm.v2.exemplar.ScanReport
import app.cash.kfsm.v2.exemplar.ScanSucceeded
import app.cash.kfsm.v2.exemplar.UploadCompleted
import app.cash.kfsm.v2.testing.InMemoryOutbox
import app.cash.kfsm.v2.testing.InMemoryPendingRequestStore
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

    "ordered effects create messages with dependencies" {
        val savedOutboxMessages = mutableListOf<OutboxMessage<String, DocumentEffect>>()

        val repository = object : Repository<String, DocumentUpload, DocumentState, DocumentEffect> {
            override fun saveWithOutbox(
                value: DocumentUpload,
                outboxMessages: List<OutboxMessage<String, DocumentEffect>>
            ): Result<DocumentUpload> {
                savedOutboxMessages.addAll(outboxMessages)
                return Result.success(value)
            }
        }

        val stateMachine = StateMachine(repository)
        val doc = DocumentUpload(id = "doc-1", state = DocumentState.Idle)

        // Create a transition that uses ordered effects
        val transition = object : Transition<String, DocumentUpload, DocumentState, DocumentEffect>(
            from = setOf(DocumentState.Idle),
            to = DocumentState.Uploading
        ) {
            override fun decide(value: DocumentUpload): Decision<DocumentState, DocumentEffect> {
                return Decision.accept(
                    state = DocumentState.Uploading,
                    Effects.ordered(
                        DocumentEffect.UploadFile("test.pdf", byteArrayOf()),
                        DocumentEffect.NotifyUser(value.id, "Upload started")
                    )
                )
            }
        }

        val result = stateMachine.transition(doc, transition)

        result.shouldBeSuccess()
        savedOutboxMessages.size shouldBe 2

        // First message should have no dependency
        savedOutboxMessages[0].dependsOnEffectId shouldBe null
        savedOutboxMessages[0].effect.shouldBeInstanceOf<DocumentEffect.UploadFile>()

        // Second message should depend on the first
        savedOutboxMessages[1].dependsOnEffectId shouldBe savedOutboxMessages[0].id
        savedOutboxMessages[1].effect.shouldBeInstanceOf<DocumentEffect.NotifyUser>()
    }

    "in-memory outbox respects effect dependencies" {
        val outbox = InMemoryOutbox<String, DocumentEffect>()

        val firstMessage: OutboxMessage<String, DocumentEffect> = OutboxMessage(
            id = "msg-1",
            valueId = "doc-1",
            effect = DocumentEffect.UploadFile("test.pdf", byteArrayOf())
        )
        val secondMessage: OutboxMessage<String, DocumentEffect> = OutboxMessage(
            id = "msg-2",
            valueId = "doc-1",
            effect = DocumentEffect.NotifyUser("doc-1", "Done"),
            dependsOnEffectId = "msg-1"
        )

        outbox.add(firstMessage)
        outbox.add(secondMessage)

        // Initially only the first message should be pending (second is blocked)
        val pending1 = outbox.fetchPending(10)
        pending1.size shouldBe 1
        pending1[0].id shouldBe "msg-1"

        // After processing the first, the second should become available
        outbox.markProcessed("msg-1")
        val pending2 = outbox.fetchPending(10)
        pending2.size shouldBe 1
        pending2[0].id shouldBe "msg-2"
    }
})
