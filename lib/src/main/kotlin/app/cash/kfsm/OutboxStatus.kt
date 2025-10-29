package app.cash.kfsm

/**
 * Represents the processing status of an outbox message.
 */
enum class OutboxStatus {
    /** The effect has been captured but not yet processed */
    PENDING,

    /** The effect is currently being processed */
    PROCESSING,

    /** The effect has been successfully executed */
    COMPLETED,

    /** The effect execution failed (may be retried) */
    FAILED
}
