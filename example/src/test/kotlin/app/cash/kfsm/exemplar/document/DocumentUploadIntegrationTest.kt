package app.cash.kfsm.exemplar.document

import app.cash.kfsm.exemplar.document.infra.JooqDocumentRepository
import app.cash.kfsm.exemplar.document.infra.MockFileStorage
import app.cash.kfsm.exemplar.document.infra.MockVirusScanner
import app.cash.kfsm.EffectProcessor
import app.cash.kfsm.StateMachine
import app.cash.kfsm.jooq.JooqOutbox
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

class DocumentUploadIntegrationTest : FunSpec({

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
  lateinit var effectProcessor: EffectProcessor<String, DocumentUpload, DocumentState, DocumentEffect>
  lateinit var scanCompleteLatch: CountDownLatch

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
    scanCompleteLatch = CountDownLatch(1)

    virusScanner = MockVirusScanner(scanDelayMs = 500) { result ->
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
          callbackStateMachine.transition(freshDoc.withScanReport(result.report), transition)
        }
      }
      scanCompleteLatch.countDown()
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

    effectProcessor = EffectProcessor(
      outbox = outbox,
      handler = effectHandler,
      stateMachine = stateMachine,
      valueLoader = { id -> repository.findById(id) }
    )

    val dslForCleanup = DSL.using(dataSource, SQLDialect.MYSQL)
    dslForCleanup.deleteFrom(DSL.table("outbox_messages")).execute()
    dslForCleanup.deleteFrom(DSL.table("document_uploads")).execute()
  }

  afterTest {
    virusScanner.close()
    fileStorage.clear()
  }

  test("successful document upload workflow - file passes virus scan") {
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

    val result = stateMachine.transition(document, StartUpload(fileContent))
    result.isSuccess shouldBe true
    result.getOrThrow().state shouldBe DocumentState.Uploading

    outbox.getStatusCounts()[app.cash.kfsm.OutboxStatus.PENDING] shouldBe 1

    effectProcessor.processAll()

    val afterUpload = repository.findById(docId).getOrThrow()!!
    afterUpload.state shouldBe DocumentState.AwaitingScan
    afterUpload.fileStorageId shouldNotBe null

    effectProcessor.processAll()

    val afterScanRequest = repository.findById(docId).getOrThrow()!!
    afterScanRequest.state shouldBe DocumentState.Scanning

    scanCompleteLatch.await(5, TimeUnit.SECONDS) shouldBe true

    val finalDoc = repository.findById(docId).getOrThrow()!!
    finalDoc.state shouldBe DocumentState.Accepted
    finalDoc.scanReport shouldNotBe null
    finalDoc.scanReport!!.virusFree shouldBe true

    fileStorage.exists(afterUpload.fileStorageId!!) shouldBe true
  }

  test("document upload workflow - file fails virus scan and is quarantined") {
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

    stateMachine.transition(document, StartUpload(fileContent))
    effectProcessor.processAll()

    val afterUpload = repository.findById(docId).getOrThrow()!!
    virusScanner.markAsInfected(afterUpload.fileStorageId!!)

    effectProcessor.processAll()
    scanCompleteLatch.await(5, TimeUnit.SECONDS)

    val finalDoc = repository.findById(docId).getOrThrow()!!
    finalDoc.state.shouldBeInstanceOf<DocumentState.Quarantined>()
    finalDoc.scanReport shouldNotBe null
    finalDoc.scanReport!!.virusFree shouldBe false
    finalDoc.scanReport!!.threatName shouldBe "EICAR-Test-File"
  }

  test("document upload fails - storage error") {
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

    stateMachine.transition(document, StartUpload(fileContent))
    effectProcessor.processAll()

    val finalDoc = repository.findById(docId).getOrThrow()!!
    finalDoc.state.shouldBeInstanceOf<DocumentState.Failed>()
    (finalDoc.state as DocumentState.Failed).reason shouldBe "Simulated upload failure for: failing-upload.pdf"
  }

  test("state transitions are validated") {
    val docId = UUID.randomUUID().toString()

    val document = DocumentUpload(
      id = docId,
      state = DocumentState.Accepted,
      fileName = "already-accepted.pdf",
      fileSize = 100,
      uploadedAt = Instant.now()
    )
    repository.create(document).getOrThrow()

    val result = stateMachine.transition(document, StartUpload("content".toByteArray()))
    result.isFailure shouldBe true
  }

  test("outbox messages are persisted atomically with state changes") {
    val docId = UUID.randomUUID().toString()
    val fileContent = "Test".toByteArray()

    val document = DocumentUpload(
      id = docId,
      state = DocumentState.Created,
      fileName = "atomic-test.pdf",
      fileSize = fileContent.size.toLong(),
      uploadedAt = Instant.now()
    )
    repository.create(document).getOrThrow()

    stateMachine.transition(document, StartUpload(fileContent))

    val dsl = DSL.using(dataSource, SQLDialect.MYSQL)

    val docState = dsl.select(DSL.field("state"))
      .from(DSL.table("document_uploads"))
      .where(DSL.field("id").eq(docId))
      .fetchOne()?.get(0, String::class.java)

    docState shouldBe "Uploading"

    val outboxCount = dsl.selectCount()
      .from(DSL.table("outbox_messages"))
      .where(DSL.field("value_id").eq(docId))
      .fetchOne(0, Int::class.java)

    outboxCount shouldBe 1
  }
})
