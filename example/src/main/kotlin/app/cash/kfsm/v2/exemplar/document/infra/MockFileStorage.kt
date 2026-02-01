package app.cash.kfsm.v2.exemplar.document.infra

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock file storage service for testing.
 *
 * Simulates an external file storage service (like S3) with in-memory storage.
 * All operations return Result for functional error handling.
 */
class MockFileStorage {

  private val files = ConcurrentHashMap<String, StoredFile>()
  private val failOnUpload = ConcurrentHashMap.newKeySet<String>()

  data class StoredFile(
    val storageId: String,
    val fileName: String,
    val content: ByteArray,
    val contentType: String = "application/octet-stream"
  ) {
    override fun equals(other: Any?) =
      other is StoredFile && storageId == other.storageId
    override fun hashCode() = storageId.hashCode()
  }

  /**
   * Upload a file and return its storage ID.
   *
   * @return Success with storage ID, or failure if upload fails
   */
  fun upload(fileName: String, content: ByteArray): Result<String> {
    if (failOnUpload.contains(fileName)) {
      return Result.failure(UploadFailure("Simulated upload failure for: $fileName"))
    }

    val storageId = "file-${UUID.randomUUID()}"
    files[storageId] = StoredFile(
      storageId = storageId,
      fileName = fileName,
      content = content
    )
    return Result.success(storageId)
  }

  /**
   * Get a file by storage ID.
   */
  fun get(storageId: String): Result<StoredFile?> =
    Result.success(files[storageId])

  /**
   * Delete a file by storage ID.
   *
   * @return Success with true if deleted, false if not found
   */
  fun delete(storageId: String): Result<Boolean> =
    Result.success(files.remove(storageId) != null)

  /**
   * Check if a file exists.
   */
  fun exists(storageId: String): Boolean = files.containsKey(storageId)

  /**
   * Mark a file name to fail on upload (for testing error scenarios).
   */
  fun markToFailOnUpload(fileName: String) {
    failOnUpload.add(fileName)
  }

  /**
   * Clear failure markers.
   */
  fun clearFailures() {
    failOnUpload.clear()
  }

  /**
   * Clear all stored files.
   */
  fun clear() {
    files.clear()
    failOnUpload.clear()
  }

  fun fileCount(): Int = files.size
}

/**
 * Error when file upload fails.
 */
class UploadFailure(message: String) : Exception(message)
