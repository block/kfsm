package app.cash.kfsm.v2.jooq

import app.cash.kfsm.v2.Effect
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.Date
import kotlin.reflect.KClass

/**
 * A Moshi-based [OutboxSerializer] for sealed class effect hierarchies.
 *
 * This serializer uses Moshi to serialize and deserialize sealed class effects.
 * The effect type is stored separately, allowing routing without deserializing the payload.
 *
 * Example:
 * ```kotlin
 * sealed class OrderEffect : Effect {
 *   data class SendEmail(val orderId: String, val email: String) : OrderEffect()
 *   data class ChargePayment(val orderId: String, val amount: Long) : OrderEffect()
 * }
 *
 * val serializer = MoshiOutboxSerializer(
 *   effectClasses = mapOf(
 *     "SendEmail" to OrderEffect.SendEmail::class,
 *     "ChargePayment" to OrderEffect.ChargePayment::class
 *   ),
 *   idSerializer = { it },  // String IDs
 *   idDeserializer = { it }
 * )
 * ```
 *
 * Java Time types (Instant, LocalDateTime, Duration, etc.) are supported out of the box.
 *
 * @param ID The type of unique identifier for values
 * @param Ef The effect type
 * @param effectClasses Map of effect type names to their KClass for deserialization
 * @param idSerializer Function to convert ID to String
 * @param idDeserializer Function to convert String back to ID
 * @param moshi Optional custom Moshi instance (defaults to one with KotlinJsonAdapterFactory)
 */
class MoshiOutboxSerializer<ID, Ef : Effect>(
  private val effectClasses: Map<String, KClass<out Ef>>,
  private val idSerializer: (ID) -> String,
  private val idDeserializer: (String) -> ID,
  private val moshi: Moshi = defaultMoshi()
) : OutboxSerializer<ID, Ef> {

  private val adapters: Map<String, JsonAdapter<out Ef>> = effectClasses.mapValues { (_, klass) ->
    moshi.adapter(klass.java)
  }

  override fun serializeEffect(effect: Ef): String {
    val effectClass = effect::class
    val typeName = effectClass.simpleName
      ?: throw IllegalArgumentException("Effect class must have a name: $effectClass")

    @Suppress("UNCHECKED_CAST")
    val adapter = adapters[typeName] as? JsonAdapter<Ef>
      ?: moshi.adapter(effectClass.java) as JsonAdapter<Ef>

    return adapter.toJson(effect)
  }

  override fun deserializeEffect(type: String, payload: String): Ef {
    val adapter = adapters[type]
      ?: throw IllegalArgumentException("Unknown effect type: $type. Known types: ${effectClasses.keys}")

    return adapter.fromJson(payload)
      ?: throw IllegalArgumentException("Failed to deserialize effect of type: $type")
  }

  override fun serializeId(id: ID): String = idSerializer(id)

  override fun deserializeId(serialized: String): ID = idDeserializer(serialized)

  companion object {
    fun defaultMoshi(): Moshi = Moshi.Builder()
      .add(Date::class.java, Rfc3339DateJsonAdapter())
      .add(Instant::class.java, InstantAdapter())
      .add(LocalDateTime::class.java, LocalDateTimeAdapter())
      .add(LocalDate::class.java, LocalDateAdapter())
      .add(LocalTime::class.java, LocalTimeAdapter())
      .add(OffsetDateTime::class.java, OffsetDateTimeAdapter())
      .add(ZonedDateTime::class.java, ZonedDateTimeAdapter())
      .add(Duration::class.java, DurationAdapter())
      .addLast(KotlinJsonAdapterFactory())
      .build()

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
      moshi: Moshi = defaultMoshi()
    ): MoshiOutboxSerializer<ID, Ef> {
      val effectClasses = sealedClass.sealedSubclasses
        .associateBy { it.simpleName ?: error("Effect class must have a name: $it") }

      return MoshiOutboxSerializer(
        effectClasses = effectClasses,
        idSerializer = idSerializer,
        idDeserializer = idDeserializer,
        moshi = moshi
      )
    }

    /**
     * Create a serializer for String IDs by scanning a sealed class.
     */
    inline fun <reified Ef : Effect> forSealedClassWithStringId(
      sealedClass: KClass<Ef>,
      moshi: Moshi = defaultMoshi()
    ): MoshiOutboxSerializer<String, Ef> = forSealedClass(
      sealedClass = sealedClass,
      idSerializer = { it },
      idDeserializer = { it },
      moshi = moshi
    )
  }
}

private class InstantAdapter : JsonAdapter<Instant>() {
  override fun fromJson(reader: com.squareup.moshi.JsonReader): Instant? =
    if (reader.peek() == com.squareup.moshi.JsonReader.Token.NULL) reader.nextNull() else Instant.parse(reader.nextString())

  override fun toJson(writer: com.squareup.moshi.JsonWriter, value: Instant?) {
    if (value == null) writer.nullValue() else writer.value(value.toString())
  }
}

private class LocalDateTimeAdapter : JsonAdapter<LocalDateTime>() {
  override fun fromJson(reader: com.squareup.moshi.JsonReader): LocalDateTime? =
    if (reader.peek() == com.squareup.moshi.JsonReader.Token.NULL) reader.nextNull() else LocalDateTime.parse(reader.nextString())

  override fun toJson(writer: com.squareup.moshi.JsonWriter, value: LocalDateTime?) {
    if (value == null) writer.nullValue() else writer.value(value.toString())
  }
}

private class LocalDateAdapter : JsonAdapter<LocalDate>() {
  override fun fromJson(reader: com.squareup.moshi.JsonReader): LocalDate? =
    if (reader.peek() == com.squareup.moshi.JsonReader.Token.NULL) reader.nextNull() else LocalDate.parse(reader.nextString())

  override fun toJson(writer: com.squareup.moshi.JsonWriter, value: LocalDate?) {
    if (value == null) writer.nullValue() else writer.value(value.toString())
  }
}

private class LocalTimeAdapter : JsonAdapter<LocalTime>() {
  override fun fromJson(reader: com.squareup.moshi.JsonReader): LocalTime? =
    if (reader.peek() == com.squareup.moshi.JsonReader.Token.NULL) reader.nextNull() else LocalTime.parse(reader.nextString())

  override fun toJson(writer: com.squareup.moshi.JsonWriter, value: LocalTime?) {
    if (value == null) writer.nullValue() else writer.value(value.toString())
  }
}

private class OffsetDateTimeAdapter : JsonAdapter<OffsetDateTime>() {
  override fun fromJson(reader: com.squareup.moshi.JsonReader): OffsetDateTime? =
    if (reader.peek() == com.squareup.moshi.JsonReader.Token.NULL) reader.nextNull() else OffsetDateTime.parse(reader.nextString())

  override fun toJson(writer: com.squareup.moshi.JsonWriter, value: OffsetDateTime?) {
    if (value == null) writer.nullValue() else writer.value(value.toString())
  }
}

private class ZonedDateTimeAdapter : JsonAdapter<ZonedDateTime>() {
  override fun fromJson(reader: com.squareup.moshi.JsonReader): ZonedDateTime? =
    if (reader.peek() == com.squareup.moshi.JsonReader.Token.NULL) reader.nextNull() else ZonedDateTime.parse(reader.nextString())

  override fun toJson(writer: com.squareup.moshi.JsonWriter, value: ZonedDateTime?) {
    if (value == null) writer.nullValue() else writer.value(value.toString())
  }
}

private class DurationAdapter : JsonAdapter<Duration>() {
  override fun fromJson(reader: com.squareup.moshi.JsonReader): Duration? =
    if (reader.peek() == com.squareup.moshi.JsonReader.Token.NULL) reader.nextNull() else Duration.parse(reader.nextString())

  override fun toJson(writer: com.squareup.moshi.JsonWriter, value: Duration?) {
    if (value == null) writer.nullValue() else writer.value(value.toString())
  }
}
