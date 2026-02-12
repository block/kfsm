package app.cash.kfsm.v2.jooq

/**
 * Serializes and deserializes value IDs and values for pending request storage.
 *
 * Implementations should handle conversion between domain types and
 * their string representations for database storage.
 *
 * @param ID The type of unique identifier for values
 * @param V The value type
 */
interface PendingRequestSerializer<ID, V> {

  /**
   * Serialize a value ID to a string.
   */
  fun serializeId(id: ID): String

  /**
   * Deserialize a value ID from its string representation.
   */
  fun deserializeId(serialized: String): ID

  /**
   * Serialize a value to a string (typically JSON).
   */
  fun serializeValue(value: V): String

  /**
   * Deserialize a value from its string representation.
   */
  fun deserializeValue(serialized: String): V
}
