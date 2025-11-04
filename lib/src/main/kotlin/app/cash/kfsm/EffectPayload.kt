package app.cash.kfsm

/**
 * Represents the serialized data for a deferrable effect.
 *
 * This payload contains all the information needed to reconstruct and execute
 * an effect at a later time by an outbox processor.
 *
 * @property effectType A unique identifier for the type of effect (e.g., "send_email", "disable_camera")
 * @property data The serialized effect data (JSON, Protocol Buffers, etc.)
 * @property metadata Optional key-value pairs for additional context (e.g., correlation IDs, trace IDs)
 */
data class EffectPayload(
    val effectType: String,
    val data: String,
    val metadata: Map<String, String> = emptyMap()
)
