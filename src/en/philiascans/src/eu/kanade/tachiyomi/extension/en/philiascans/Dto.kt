package eu.kanade.tachiyomi.extension.en.philiascans

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class Dto(
    private val slug: String,
    private val number: String,
    private val title: String? = null,
    private val lang: String,
    private val publishedAt: String,
    val coinPrice: Int = 0,
) {
    fun toSChapter(mangaUrl: String): SChapter = SChapter.create().apply {
        url = "$mangaUrl/$slug?lang=$lang"
        val validTitle = title?.takeIf { it.isNotBlank() && it != "null" && it != number }
        name = buildString {
            append("Ch. ")
            append(number)
            if (validTitle != null) {
                append(" - ")
                append(validTitle)
            }
            if (coinPrice > 0) {
                append(" 🔒")
            }
        }
        date_upload = isoDateFormat.tryParse(publishedAt)
    }
}

private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
