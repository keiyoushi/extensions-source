package eu.kanade.tachiyomi.extension.en.mehgazone.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeSerializer : KSerializer<Calendar> {
    private val sdf by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US) }

    override val descriptor = PrimitiveSerialDescriptor("DateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Calendar) = encoder.encodeString(sdf.format(value.time))
    override fun deserialize(decoder: Decoder): Calendar {
        val cal = Calendar.getInstance()
        var date: Date? = null
        try {
            date = sdf.parse(decoder.decodeString())
        } catch (_: ParseException) { }
        if (date != null) {
            cal.time = date
        }
        return cal
    }
}
