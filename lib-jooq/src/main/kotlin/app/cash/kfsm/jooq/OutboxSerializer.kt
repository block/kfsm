package app.cash.kfsm.jooq

import app.cash.kfsm.Effect

/**
 * Serializes and deserializes effects and value IDs for outbox storage.
 *
 * Implementations should handle conversion between domain types and
 * their string representations for database storage.
 *
 * @param ID The type of unique identifier for values
 * @param Ef The effect type
 */
interface OutboxSerializer<ID, Ef : Effect> {

  /**
   * Serialize an effect to a string (typically JSON).
   */
  fun serializeEffect(effect: Ef): String

  /**
   * Deserialize an effect from its type and payload.
   *
   * @param type The effect type identifier (typically the simple class name)
   * @param payload The serialized effect data
   */
  fun deserializeEffect(type: String, payload: String): Ef

  /**
   * Serialize a value ID to a string.
   */
  fun serializeId(id: ID): String

  /**
   * Deserialize a value ID from its string representation.
   */
  fun deserializeId(serialized: String): ID
}
