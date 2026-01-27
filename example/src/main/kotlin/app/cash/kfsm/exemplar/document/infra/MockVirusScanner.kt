package app.cash.kfsm.exemplar.document.infra

import app.cash.kfsm.exemplar.document.ScanReport
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Mock virus scanner service that runs on a separate thread pool.
 *
 * Simulates an external virus scanning service with configurable behavior:
 * - Async scanning with configurable delay
 * - Ability to mark specific files as infected
 * - Callbacks when scans complete
 *
 * All operations return Result for functional error handling.
 */
class MockVirusScanner(
  private val scanDelayMs: Long = 500,
  private val onScanComplete: (ScanResult) -> Unit
) : AutoCloseable {

  private val executor = Executors.newSingleThreadExecutor { r ->
    Thread(r, "virus-scanner").apply { isDaemon = true }
  }

  private val infectedFiles = ConcurrentHashMap.newKeySet<String>()
  private val pendingScans = ConcurrentHashMap<String, ScanRequest>()

  data class ScanRequest(
    val documentId: String,
    val fileStorageId: String,
    val submittedAt: Instant = Instant.now()
  )

  sealed class ScanResult {
    abstract val documentId: String
    abstract val fileStorageId: String
    abstract val report: ScanReport

    data class Clean(
      override val documentId: String,
      override val fileStorageId: String,
      override val report: ScanReport
    ) : ScanResult()

    data class Infected(
      override val documentId: String,
      override val fileStorageId: String,
      override val report: ScanReport
    ) : ScanResult()

    data class Error(
      override val documentId: String,
      override val fileStorageId: String,
      override val report: ScanReport,
      val error: String
    ) : ScanResult()
  }

  /**
   * Submit a file for virus scanning. Returns immediately.
   * The scan runs asynchronously and calls [onScanComplete] when done.
   *
   * @return Success with scan ID, or failure if submission fails
   */
  fun submitScan(documentId: String, fileStorageId: String): Result<String> {
    val scanId = UUID.randomUUID().toString()
    val request = ScanRequest(documentId, fileStorageId)
    pendingScans[scanId] = request

    executor.submit {
      try {
        Thread.sleep(scanDelayMs)
        val result = performScan(request)
        pendingScans.remove(scanId)
        onScanComplete(result)
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      } catch (e: Exception) {
        pendingScans.remove(scanId)
        onScanComplete(
          ScanResult.Error(
            documentId = request.documentId,
            fileStorageId = request.fileStorageId,
            report = ScanReport(
              scannedAt = Instant.now(),
              virusFree = false,
              checksum = "",
              threatName = null
            ),
            error = e.message ?: "Unknown error"
          )
        )
      }
    }

    return Result.success(scanId)
  }

  private fun performScan(request: ScanRequest): ScanResult {
    val isInfected = infectedFiles.contains(request.fileStorageId)
    val checksum = UUID.randomUUID().toString().replace("-", "").take(32)

    val report = ScanReport(
      scannedAt = Instant.now(),
      virusFree = !isInfected,
      checksum = checksum,
      threatName = if (isInfected) "EICAR-Test-File" else null
    )

    return if (isInfected) {
      ScanResult.Infected(request.documentId, request.fileStorageId, report)
    } else {
      ScanResult.Clean(request.documentId, request.fileStorageId, report)
    }
  }

  /**
   * Mark a file storage ID as infected for testing.
   */
  fun markAsInfected(fileStorageId: String) {
    infectedFiles.add(fileStorageId)
  }

  /**
   * Clear the infected files list.
   */
  fun clearInfectedFiles() {
    infectedFiles.clear()
  }

  fun pendingScanCount(): Int = pendingScans.size

  override fun close() {
    executor.shutdown()
    executor.awaitTermination(5, TimeUnit.SECONDS)
  }
}
