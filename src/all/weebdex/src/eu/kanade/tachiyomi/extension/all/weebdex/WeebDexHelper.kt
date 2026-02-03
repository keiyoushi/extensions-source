package eu.kanade.tachiyomi.extension.all.weebdex

import eu.kanade.tachiyomi.extension.all.weebdex.dto.CoverDto
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WeebDexHelper {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    fun parseStatus(status: String?): Int = when (status?.lowercase(Locale.ROOT)) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    fun buildCoverUrl(mangaId: String, cover: CoverDto?, coverQuality: String): String? {
        if (cover == null) return null
        val ext = when (coverQuality) {
            "256" -> ".256.webp"
            "512" -> ".512.webp"
            else -> cover.ext
        }
        return "${WeebDexConstants.CDN_COVER_URL}/$mangaId/${cover.id}$ext"
    }

    fun parseDate(dateStr: String): Long = dateFormat.tryParse(dateStr)

    fun parseChapterNumber(chapter: String?): Float {
        if (chapter.isNullOrBlank()) return -2F
        val regex = Regex("""\d+(\.?\d+)?""")
        val match = regex.find(chapter)
        return match?.value?.toFloatOrNull() ?: -2F
    }
}
