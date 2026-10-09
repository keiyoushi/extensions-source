package keiyoushi.utils.reactFlight

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale

/** A [Date] whose React Flight `$D<iso>` string is parsed by [ReactFlightDateSerializer]. */
typealias ReactFlightDate =
    @Serializable(with = ReactFlightDateSerializer::class)
    Date

object ReactFlightDateSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ReactFlightDate", PrimitiveKind.STRING)
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)

    override fun serialize(encoder: Encoder, value: Date): Unit = throw SerializationException("Stub !")

    override fun deserialize(decoder: Decoder): Date {
        val dateString = decoder.decodeString()
        return try {
            Date.from(LocalDateTime.parse(dateString, format).toInstant(ZoneOffset.UTC))
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Failed to parse date: $dateString", e)
        }
    }
}
