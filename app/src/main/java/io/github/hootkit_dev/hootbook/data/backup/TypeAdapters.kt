package io.github.hootkit_dev.hootbook.data.backup

import com.google.gson.*
import java.lang.reflect.Type
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Gson TypeAdapter for LocalDateTime
 */
class LocalDateTimeTypeAdapter : JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    
    override fun serialize(
        src: LocalDateTime?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src?.format(formatter))
    }
    
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LocalDateTime? {
        return json?.asString?.let { 
            LocalDateTime.parse(it, formatter)
        }
    }
}

/**
 * Gson TypeAdapter for BigDecimal
 */
class BigDecimalTypeAdapter : JsonSerializer<BigDecimal>, JsonDeserializer<BigDecimal> {
    override fun serialize(
        src: BigDecimal?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src?.toPlainString())
    }
    
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): BigDecimal? {
        return json?.asString?.let { BigDecimal(it) }
    }
}
/**
 * Backups made before sync ids existed have no `syncId`; Gson then leaves the field null, which a
 * non-null Kotlin String cannot hold and the NOT NULL column refuses. Reads it as empty instead,
 * so the record gets a fresh id when it is written (see SyncTriggers).
 */
class SyncIdDefaultsFactory : TypeAdapterFactory {
    override fun <T : Any?> create(gson: Gson, type: com.google.gson.reflect.TypeToken<T>): TypeAdapter<T>? {
        val field = runCatching { type.rawType.getDeclaredField(FIELD) }.getOrNull()
            ?.takeIf { it.type == String::class.java } ?: return null
        field.isAccessible = true
        val delegate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T>() {
            override fun write(out: com.google.gson.stream.JsonWriter, value: T) = delegate.write(out, value)

            override fun read(reader: com.google.gson.stream.JsonReader): T {
                val value = delegate.read(reader)
                if (value != null && field.get(value) == null) field.set(value, "")
                return value
            }
        }
    }

    private companion object {
        const val FIELD = "syncId"
    }
}
