package keiyoushi.utils.reactFlight

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable(with = ReactFlightDateSerializer::class)
class ReactFlightDate : Date {
    constructor(date: Date) : super(date.time)
}

object ReactFlightDateSerializer : KSerializer<ReactFlightDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ReactFlightDate", PrimitiveKind.STRING)
    private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun serialize(encoder: Encoder, value: ReactFlightDate): Unit = throw SerializationException("Stub !")

    override fun deserialize(decoder: Decoder): ReactFlightDate {
        val dateString = decoder.decodeString()
        return ReactFlightDate(format.parse(dateString) ?: throw IllegalArgumentException("Failed to parse date: $dateString"))
    }
}
