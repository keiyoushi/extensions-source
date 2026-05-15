package keiyoushi.utils.reactFlight

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/**
 * [java.lang.Double] is `final`, so unlike [ReactFlightDate] (extends [java.util.Date])
 * and [ReactFlightBigInt] (extends [java.math.BigInteger]) a numeric value cannot be a
 * true subtype. This extends [Number] and delegates, so it stays usable wherever a
 * [Number] is expected; read the concrete value via [toDouble]/[toLong]/etc.
 */
@Serializable(with = ReactFlightNumberSerializer::class)
class ReactFlightNumber(val value: Double) : Number() {
    override fun toByte(): Byte = value.toInt().toByte()
    override fun toShort(): Short = value.toInt().toShort()
    override fun toInt(): Int = value.toInt()
    override fun toLong(): Long = value.toLong()
    override fun toFloat(): Float = value.toFloat()
    override fun toDouble(): Double = value
    override fun toString(): String = value.toString()
}

object ReactFlightNumberSerializer : KSerializer<ReactFlightNumber> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ReactFlightNumber", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ReactFlightNumber): Unit = throw SerializationException("Stub !")

    override fun deserialize(decoder: Decoder): ReactFlightNumber {
        // Value may be a bare JSON number (3.14) or a token string already stripped of its
        // leading '$' by resolveNextJsRefs ("Infinity"/"-Infinity"/"NaN"/"-0").
        val raw = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content
        val value = when (raw) {
            "Infinity" -> Double.POSITIVE_INFINITY
            "-Infinity" -> Double.NEGATIVE_INFINITY
            "NaN" -> Double.NaN
            "-0" -> -0.0
            else -> raw.toDoubleOrNull()
                ?: throw IllegalArgumentException("Failed to parse Number: $raw")
        }
        return ReactFlightNumber(value)
    }
}
