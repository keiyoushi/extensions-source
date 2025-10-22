import eu.kanade.tachiyomi.extension.en.weebdex.WeebDexConstants
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WeebDexHelper {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    fun parseStatus(status: String?): Int {
        return when (status?.lowercase(Locale.ROOT)) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    fun buildCoverUrl(mangaId: String, cover: eu.kanade.tachiyomi.extension.en.weebdex.dto.CoverDto?): String? {
        if (cover == null) return null
        val ext = cover.ext
        return "${WeebDexConstants.CDN_COVER_URL}/$mangaId/${cover.id}$ext"
    }

    fun parseDate(dateStr: String): Long {
        return dateFormat.tryParse(dateStr)
    }
}
