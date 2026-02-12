package app.cash.kfsm.v2.exemplar.document

import app.cash.kfsm.v2.exemplar.document.infra.DocumentPendingRequestSerializer
import app.cash.kfsm.v2.exemplar.document.infra.JooqDocumentRepository
import app.cash.kfsm.v2.exemplar.document.infra.MockFileStorage
import app.cash.kfsm.v2.jooq.JooqOutbox
import app.cash.kfsm.v2.jooq.JooqPendingRequestStore
import app.cash.kfsm.v2.exemplar.document.infra.MockVirusScanner
import app.cash.kfsm.v2.AwaitableStateMachine
import app.cash.kfsm.v2.EffectProcessor
import app.cash.kfsm.v2.PendingRequestStore
import app.cash.kfsm.v2.StateMachine
import app.cash.kfsm.v2.WorkflowTimeoutException
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AwaitableDocumentUploadIntegrationTest : FunSpec({

  val mysql = MySQLContainer(DockerImageName.parse("mysql:8.0"))
    .withDatabaseName("kfsm_test")
    .withUsername("test")
    .withPassword("test")

  lateinit var dataSource: DataSource
  lateinit var repository: JooqDocumentRepository
  lateinit var outbox: JooqOutbox<String, DocumentEffect>
  lateinit var fileStorage: MockFileStorage
  lateinit var virusScanner: MockVirusScanner
  lateinit var notifications: CopyOnWriteArrayList<Pair<String, String>>
  lateinit var stateMachine: StateMachine<String, DocumentUpload, DocumentState, DocumentEffect>
  lateinit var awaitableStateMachine: AwaitableStateMachine<String, DocumentUpload, DocumentState, DocumentEffect>
  lateinit var effectProcessor: EffectProcessor<String, DocumentUpload, DocumentState, DocumentEffect>
  lateinit var pendingRequestStore: PendingRequestStore<String, DocumentUpload>

  fun isSettled(state: DocumentState): Boolean = when (state) {
    is DocumentState.Accepted -> true
    is DocumentState.Quarantined -> true
    is DocumentState.Failed -> true
    else -> false
  }

  beforeSpec {
    mysql.start()

    val hikariConfig = HikariConfig().apply {
      jdbcUrl = mysql.jdbcUrl
      username = mysql.username
      password = mysql.password
      maximumPoolSize = 5
    }
    dataSource = HikariDataSource(hikariConfig)

    dataSource.connection.use { conn ->
      conn.createStatement().use { stmt ->
        val schema = this::class.java.classLoader
          .getResourceAsStream("schema.sql")!!
          .bufferedReader()
          .readText()
        schema.split(";")
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .forEach { sql ->
            stmt.execute(sql)
          }
      }
    }
  }

  afterSpec {
    mysql.stop()
  }

  beforeTest {
    val dsl = DSL.using(dataSource, SQLDialect.MYSQL)

    repository = JooqDocumentRepository(dsl)
    outbox = repository.getOutbox()
    fileStorage = MockFileStorage()
    notifications = CopyOnWriteArrayList()
    pendingRequestStore = JooqPendingRequestStore(
      dsl = dsl,
      serializer = DocumentPendingRequestSerializer.instance
    )

    virusScanner = MockVirusScanner(scanDelayMs = 50) { result ->
      val transition = when (result) {
        is MockVirusScanner.ScanResult.Clean ->
          ScanPassed(result.report)
        is MockVirusScanner.ScanResult.Infected ->
          ScanFailed(result.report)
        is MockVirusScanner.ScanResult.Error ->
          ErrorOccurred(result.error)
      }

      // Wait for document to reach Scanning state before applying the scan result transition
      var doc: DocumentUpload? = null
      repeat(50) { // up to 5 seconds
        doc = repository.findById(result.documentId).getOrNull()
        if (doc?.state == DocumentState.Scanning) return@repeat
        Thread.sleep(100)
      }

      if (doc != null && doc!!.state == DocumentState.Scanning) {
        // Use a fresh DSL context and repository to avoid transaction conflicts
        val callbackDsl = DSL.using(dataSource, SQLDialect.MYSQL)
        val callbackRepo = JooqDocumentRepository(callbackDsl)
        val callbackStateMachine = StateMachine(callbackRepo)
        
        val freshDoc = callbackRepo.findById(result.documentId).getOrNull()
        if (freshDoc?.state == DocumentState.Scanning) {
          val transitionResult = callbackStateMachine.transition(freshDoc.withScanReport(result.report), transition)
          transitionResult.onSuccess { finalDoc ->
            awaitableStateMachine.markCompleted(result.documentId, finalDoc)
          }
          transitionResult.onFailure { error ->
            awaitableStateMachine.markFailed(result.documentId, error)
          }
        }
      }
    }

    val notificationSink = DocumentEffectHandler.NotificationSink { documentId, message ->
      notifications.add(documentId to message)
      Result.success(Unit)
    }

    val effectHandler = DocumentEffectHandler(
      fileStorage = fileStorage,
      virusScanner = virusScanner,
      notificationSink = notificationSink,
      repository = object : DocumentEffectHandler.DocumentRepository {
        override fun findById(id: String) = repository.findById(id)
        override fun updateWithFields(document: DocumentUpload) = repository.updateWithFields(document)
      }
    )

    stateMachine = StateMachine(repository)

    awaitableStateMachine = AwaitableStateMachine(
      stateMachine = stateMachine,
      pendingRequestStore = pendingRequestStore,
      isSettled = ::isSettled,
      pollInterval = 50.milliseconds
    )

    effectProcessor = EffectProcessor(
      outbox = outbox,
      handler = effectHandler,
      stateMachine = stateMachine,
      valueLoader = { id -> repository.findById(id) },
      awaitable = awaitableStateMachine
    )

    val dslForCleanup = DSL.using(dataSource, SQLDialect.MYSQL)
    dslForCleanup.deleteFrom(DSL.table("outbox_messages")).execute()
    dslForCleanup.deleteFrom(DSL.table("pending_requests")).execute()
    dslForCleanup.deleteFrom(DSL.table("document_uploads")).execute()
  }

  afterTest {
    virusScanner.close()
    fileStorage.clear()
  }

  test("transitionAndAwait - successful document upload workflow awaits terminal state") {
    val docId = UUID.randomUUID().toString()
    val fileContent = "Hello, World!".toByteArray()

    val document = DocumentUpload(
      id = docId,
      state = DocumentState.Created,
      fileName = "test-document.pdf",
      fileSize = fileContent.size.toLong(),
      uploadedAt = Instant.now()
    )
    repository.create(document).getOrThrow()

    val stopProcessing = AtomicBoolean(false)
    launch(Dispatchers.IO) {
      while (!stopProcessing.get()) {
        effectProcessor.processAll()
        delay(25)
      }
    }

    val result = try {
      awaitableStateMachine.transitionAndAwait(
        value = document,
        transition = StartUpload(fileContent),
        timeout = 10.seconds
      )
    } finally {
      stopProcessing.set(true)
    }

    result.isSuccess shouldBe true
    val finalDoc = result.getOrThrow()
    finalDoc.state shouldBe DocumentState.Accepted
    finalDoc.scanReport shouldNotBe null
    finalDoc.scanReport!!.virusFree shouldBe true
  }

  test("transitionAndAwait - document fails virus scan returns quarantined state") {
    val docId = UUID.randomUUID().toString()
    val fileContent = "Malicious content".toByteArray()

    val document = DocumentUpload(
      id = docId,
      state = DocumentState.Created,
      fileName = "malware.exe",
      fileSize = fileContent.size.toLong(),
      uploadedAt = Instant.now()
    )
    repository.create(document).getOrThrow()

    val stopProcessing = AtomicBoolean(false)
    val markedInfected = AtomicBoolean(false)
    launch(Dispatchers.IO) {
      while (!stopProcessing.get()) {
        effectProcessor.processAll()
        if (!markedInfected.get()) {
          val afterUpload = repository.findById(docId).getOrNull()
          if (afterUpload?.fileStorageId != null) {
            virusScanner.markAsInfected(afterUpload.fileStorageId!!)
            markedInfected.set(true)
          }
        }
        // Check for terminal state and mark completed
        val currentDoc = repository.findById(docId).getOrNull()
        if (currentDoc != null) {
          awaitableStateMachine.markCompleted(docId, currentDoc)
        }
        delay(25)
      }
    }

    val result = try {
      awaitableStateMachine.transitionAndAwait(
        value = document,
        transition = StartUpload(fileContent),
        timeout = 10.seconds
      )
    } finally {
      stopProcessing.set(true)
    }

    result.isSuccess shouldBe true
    val finalDoc = result.getOrThrow()
    finalDoc.state.shouldBeInstanceOf<DocumentState.Quarantined>()
    finalDoc.scanReport shouldNotBe null
    finalDoc.scanReport!!.virusFree shouldBe false
  }

  test("transitionAndAwait - storage error returns failed state") {
    val docId = UUID.randomUUID().toString()
    val fileContent = "Test content".toByteArray()

    fileStorage.markToFailOnUpload("failing-upload.pdf")

    val document = DocumentUpload(
      id = docId,
      state = DocumentState.Created,
      fileName = "failing-upload.pdf",
      fileSize = fileContent.size.toLong(),
      uploadedAt = Instant.now()
    )
    repository.create(document).getOrThrow()

    val stopProcessing = AtomicBoolean(false)
    launch(Dispatchers.IO) {
      while (!stopProcessing.get()) {
        effectProcessor.processAll()
        val currentDoc = repository.findById(docId).getOrNull()
        if (currentDoc != null) {
          awaitableStateMachine.markCompleted(docId, currentDoc)
        }
        delay(25)
      }
    }

    val result = try {
      awaitableStateMachine.transitionAndAwait(
        value = document,
        transition = StartUpload(fileContent),
        timeout = 10.seconds
      )
    } finally {
      stopProcessing.set(true)
    }

    result.isSuccess shouldBe true
    val finalDoc = result.getOrThrow()
    finalDoc.state.shouldBeInstanceOf<DocumentState.Failed>()
    (finalDoc.state as DocumentState.Failed).reason shouldBe "Simulated upload failure for: failing-upload.pdf"
  }

  test("transitionAndAwait - invalid transition returns failure immediately") {
    val docId = UUID.randomUUID().toString()

    val document = DocumentUpload(
      id = docId,
      state = DocumentState.Accepted,
      fileName = "already-accepted.pdf",
      fileSize = 100,
      uploadedAt = Instant.now()
    )
    repository.create(document).getOrThrow()

    val result = awaitableStateMachine.transitionAndAwait(
      value = document,
      transition = StartUpload("content".toByteArray()),
      timeout = 5.seconds
    )

    result.isFailure shouldBe true
  }

  test("transitionAndAwait - timeout when workflow doesn't complete") {
    val docId = UUID.randomUUID().toString()
    val fileContent = "Test content".toByteArray()

    val document = DocumentUpload(
      id = docId,
      state = DocumentState.Created,
      fileName = "slow-document.pdf",
      fileSize = fileContent.size.toLong(),
      uploadedAt = Instant.now()
    )
    repository.create(document).getOrThrow()

    val result = awaitableStateMachine.transitionAndAwait(
      value = document,
      transition = StartUpload(fileContent),
      timeout = 200.milliseconds
    )

    result.isFailure shouldBe true
    result.exceptionOrNull().shouldBeInstanceOf<WorkflowTimeoutException>()
  }
})
