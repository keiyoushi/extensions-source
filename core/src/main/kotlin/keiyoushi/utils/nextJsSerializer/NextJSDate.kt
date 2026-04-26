package keiyoushi.utils.nextJsSerializer

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

@Serializable(with = NextJSDateSerializer::class)
class NextJSDate : Date {
    constructor(date: Date) : super(date.time)
}

object NextJSDateSerializer : KSerializer<NextJSDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NextJSDate", PrimitiveKind.STRING)
    private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun serialize(encoder: Encoder, value: NextJSDate): Unit = throw SerializationException("Stub !")

    override fun deserialize(decoder: Decoder): NextJSDate {
        val dateString = decoder.decodeString()
        return NextJSDate(format.parse(dateString) ?: throw IllegalArgumentException("Failed to parse date: $dateString"))
    }
}
