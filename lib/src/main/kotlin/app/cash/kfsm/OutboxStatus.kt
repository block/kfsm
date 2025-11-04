package app.cash.kfsm

import app.cash.kfsm.annotations.ExperimentalLibraryApi

/**
 * Represents the processing status of an outbox message.
 */
@ExperimentalLibraryApi
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
