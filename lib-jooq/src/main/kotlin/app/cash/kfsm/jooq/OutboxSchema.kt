package app.cash.kfsm.jooq

/**
 * SQL schema for the outbox table.
 *
 * This object provides DDL statements for creating the outbox table
 * in MySQL. Adapt as needed for other databases.
 */
object OutboxSchema {

  /**
   * MySQL DDL for creating the outbox table.
   *
   * @param tableName The table name (default: "outbox")
   */
  fun mysql(tableName: String = "outbox"): String = """
    CREATE TABLE IF NOT EXISTS `$tableName` (
      `id` VARCHAR(36) NOT NULL,
      `value_id` VARCHAR(255) NOT NULL,
      `effect_type` VARCHAR(255) NOT NULL,
      `effect_payload` TEXT NOT NULL,
      `dedup_key` VARCHAR(255) NULL,
      `depends_on_effect_id` VARCHAR(36) NULL,
      `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
      `attempt_count` INT NOT NULL DEFAULT 0,
      `last_error` TEXT NULL,
      `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
      `processed_at` TIMESTAMP(6) NULL,
      PRIMARY KEY (`id`),
      INDEX `idx_${tableName}_pending` (`status`, `created_at`),
      INDEX `idx_${tableName}_value_id` (`value_id`, `status`, `created_at`),
      INDEX `idx_${tableName}_dedup` (`dedup_key`),
      INDEX `idx_${tableName}_depends` (`depends_on_effect_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  """.trimIndent()

  /**
   * PostgreSQL DDL for creating the outbox table.
   *
   * @param tableName The table name (default: "outbox")
   */
  fun postgresql(tableName: String = "outbox"): String = """
    CREATE TABLE IF NOT EXISTS "$tableName" (
      id VARCHAR(36) NOT NULL,
      value_id VARCHAR(255) NOT NULL,
      effect_type VARCHAR(255) NOT NULL,
      effect_payload TEXT NOT NULL,
      dedup_key VARCHAR(255),
      depends_on_effect_id VARCHAR(36),
      status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
      attempt_count INT NOT NULL DEFAULT 0,
      last_error TEXT,
      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
      processed_at TIMESTAMP WITH TIME ZONE,
      PRIMARY KEY (id)
    );
    
    CREATE INDEX IF NOT EXISTS idx_${tableName}_pending ON "$tableName" (status, created_at);
    CREATE INDEX IF NOT EXISTS idx_${tableName}_value_id ON "$tableName" (value_id, status, created_at);
    CREATE INDEX IF NOT EXISTS idx_${tableName}_dedup ON "$tableName" (dedup_key);
    CREATE INDEX IF NOT EXISTS idx_${tableName}_depends ON "$tableName" (depends_on_effect_id);
  """.trimIndent()
}
