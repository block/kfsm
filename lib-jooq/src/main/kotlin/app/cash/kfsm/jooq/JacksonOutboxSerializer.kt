package app.cash.kfsm.jooq

import app.cash.kfsm.Effect
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlin.reflect.KClass

/**
 * A Jackson-based [OutboxSerializer] for sealed class effect hierarchies.
 *
 * This serializer uses Jackson's polymorphic type handling to serialize
 * and deserialize sealed class effects. The effect type is stored separately,
 * allowing routing without deserializing the payload.
 *
 * Example:
 * ```kotlin
 * sealed class OrderEffect : Effect {
 *   data class SendEmail(val orderId: String, val email: String) : OrderEffect()
 *   data class ChargePayment(val orderId: String, val amount: Long) : OrderEffect()
 * }
 *
 * val serializer = JacksonOutboxSerializer(
 *   effectClasses = mapOf(
 *     "SendEmail" to OrderEffect.SendEmail::class,
 *     "ChargePayment" to OrderEffect.ChargePayment::class
 *   ),
 *   idSerializer = { it },  // String IDs
 *   idDeserializer = { it }
 * )
 * ```
 *
 * @param ID The type of unique identifier for values
 * @param Ef The effect type
 * @param effectClasses Map of effect type names to their KClass for deserialization
 * @param idSerializer Function to convert ID to String
 * @param idDeserializer Function to convert String back to ID
 * @param objectMapper Optional custom ObjectMapper (defaults to one with Kotlin and JavaTime modules)
 */
class JacksonOutboxSerializer<ID, Ef : Effect>(
  private val effectClasses: Map<String, KClass<out Ef>>,
  private val idSerializer: (ID) -> String,
  private val idDeserializer: (String) -> ID,
  private val objectMapper: ObjectMapper = defaultObjectMapper()
) : OutboxSerializer<ID, Ef> {

  override fun serializeEffect(effect: Ef): String {
    return objectMapper.writeValueAsString(effect)
  }

  override fun deserializeEffect(type: String, payload: String): Ef {
    val effectClass = effectClasses[type]
      ?: throw IllegalArgumentException("Unknown effect type: $type. Known types: ${effectClasses.keys}")

    @Suppress("UNCHECKED_CAST")
    return objectMapper.readValue(payload, effectClass.java) as Ef
  }

  override fun serializeId(id: ID): String = idSerializer(id)

  override fun deserializeId(serialized: String): ID = idDeserializer(serialized)

  companion object {
    fun defaultObjectMapper(): ObjectMapper = ObjectMapper()
      .registerModule(KotlinModule.Builder().build())
      .registerModule(JavaTimeModule())

    /**
     * Create a serializer by scanning a sealed class for its subclasses.
     *
     * @param sealedClass The sealed effect class
     * @param idSerializer Function to convert ID to String
     * @param idDeserializer Function to convert String back to ID
     */
    inline fun <ID, reified Ef : Effect> forSealedClass(
      sealedClass: KClass<Ef>,
      noinline idSerializer: (ID) -> String,
      noinline idDeserializer: (String) -> ID,
      objectMapper: ObjectMapper = defaultObjectMapper()
    ): JacksonOutboxSerializer<ID, Ef> {
      val effectClasses = sealedClass.sealedSubclasses
        .associateBy { it.simpleName ?: error("Effect class must have a name: $it") }

      return JacksonOutboxSerializer(
        effectClasses = effectClasses,
        idSerializer = idSerializer,
        idDeserializer = idDeserializer,
        objectMapper = objectMapper
      )
    }

    /**
     * Create a serializer for String IDs by scanning a sealed class.
     */
    inline fun <reified Ef : Effect> forSealedClassWithStringId(
      sealedClass: KClass<Ef>,
      objectMapper: ObjectMapper = defaultObjectMapper()
    ): JacksonOutboxSerializer<String, Ef> = forSealedClass(
      sealedClass = sealedClass,
      idSerializer = { it },
      idDeserializer = { it },
      objectMapper = objectMapper
    )
  }
}
