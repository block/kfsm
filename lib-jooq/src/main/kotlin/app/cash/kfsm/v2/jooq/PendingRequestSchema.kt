package app.cash.kfsm.v2.jooq

/**
 * SQL schema for the pending_requests table.
 *
 * This object provides DDL statements for creating the pending_requests table
 * in MySQL and PostgreSQL.
 */
object PendingRequestSchema {

  /**
   * MySQL DDL for creating the pending_requests table.
   *
   * @param tableName The table name (default: "pending_requests")
   */
  fun mysql(tableName: String = "pending_requests"): String = """
    CREATE TABLE IF NOT EXISTS `$tableName` (
      `id` VARCHAR(36) NOT NULL,
      `value_id` VARCHAR(255) NOT NULL,
      `status` VARCHAR(20) NOT NULL DEFAULT 'WAITING',
      `result_payload` TEXT NULL,
      `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
      `updated_at` TIMESTAMP(6) NULL,
      PRIMARY KEY (`id`),
      INDEX `idx_${tableName}_value_status` (`value_id`, `status`),
      INDEX `idx_${tableName}_status_created` (`status`, `created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  """.trimIndent()

  /**
   * PostgreSQL DDL for creating the pending_requests table.
   *
   * @param tableName The table name (default: "pending_requests")
   */
  fun postgresql(tableName: String = "pending_requests"): String = """
    CREATE TABLE IF NOT EXISTS "$tableName" (
      id VARCHAR(36) NOT NULL,
      value_id VARCHAR(255) NOT NULL,
      status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
      result_payload TEXT,
      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMP WITH TIME ZONE,
      PRIMARY KEY (id)
    );
    
    CREATE INDEX IF NOT EXISTS idx_${tableName}_value_status ON "$tableName" (value_id, status);
    CREATE INDEX IF NOT EXISTS idx_${tableName}_status_created ON "$tableName" (status, created_at);
  """.trimIndent()
}
