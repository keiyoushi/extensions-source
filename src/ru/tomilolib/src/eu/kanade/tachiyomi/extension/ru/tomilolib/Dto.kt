package eu.kanade.tachiyomi.extension.ru.tomilolib

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class ApiResponse<T>(
    val data: T,
)

@Serializable
class TitlesData(
    val titles: List<TitleDto> = emptyList(),
    val pagination: Pagination = Pagination(),
)

@Serializable
class Pagination(
    val page: Int = 1,
    val pages: Int = 1,
)

@Serializable
class TitleDto(
    @SerialName("_id") private val id: String,
    private val name: String,
    private val slug: String = "",
    private val altNames: List<String> = emptyList(),
    private val description: String = "",
    private val genres: List<String> = emptyList(),
    private val coverImage: String? = null,
    private val status: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    val isAdult: Boolean = false,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = "$slug/$id"
        title = name.trim()
        thumbnail_url = resolveImageUrl(coverImage, baseUrl)
        author = this@TitleDto.author
        artist = this@TitleDto.artist
        genre = genres.joinToString()
        status = parseStatus(this@TitleDto.status)
        description = buildString {
            val desc = this@TitleDto.description
            if (desc.isNotBlank()) append(desc.trim())
            val others = altNames.filter { it.isNotBlank() }
            if (others.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Альтернативные названия: ")
                append(others.joinToString(" / "))
            }
        }
    }
}

@Serializable
class ChaptersData(
    val chapters: List<ChapterDto> = emptyList(),
    val pagination: Pagination = Pagination(),
)

@Serializable
class ChapterDto(
    @SerialName("_id") private val id: String,
    val chapterNumber: Double = 0.0,
    private val name: String? = null,
    private val releaseDate: String? = null,
    val isPublished: Boolean = true,
    private val isPaid: Boolean = false,
    private val unlockPrice: Int = 0,
    private val freeAt: String? = null,
) {
    fun toSChapter(hidePaid: Boolean): SChapter? {
        val locked = isPaid && unlockPrice > 0 && isLockedNow(freeAt)
        if (locked && hidePaid) return null
        return SChapter.create().apply {
            url = id
            name = this@ChapterDto.name ?: "Глава ${chapterNumber.toString().removeSuffix(".0")}"
            chapter_number = chapterNumber.toFloat()
            date_upload = DATE_FORMAT.tryParse(releaseDate)
            if (locked) {
                scanlator = "🔒 Платно"
            }
        }
    }
}

@Serializable
class ChapterDetailDto(
    val pages: List<String> = emptyList(),
    val isPaid: Boolean = false,
)

private const val CDN_URL = "https://tomilolib.s3.regru.cloud"

private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun parseStatus(status: String?): Int = when (status) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "pause", "frozen" -> SManga.ON_HIATUS
    else -> SManga.UNKNOWN
}

private fun isLockedNow(freeAt: String?): Boolean {
    val ts = DATE_FORMAT.tryParse(freeAt)
    return ts == 0L || ts > System.currentTimeMillis()
}

// "/uploads/..." objects are not publicly accessible on the S3 CDN (403),
// but the same objects are served from the CDN root without that prefix.
fun resolveImageUrl(path: String?, baseUrl: String): String {
    if (path.isNullOrBlank()) return ""
    val url = when {
        path.startsWith("http") -> path
        path.startsWith("/") -> CDN_URL + path
        else -> "$CDN_URL/$path"
    }
    return when {
        url.startsWith("$CDN_URL/uploads") -> url.replace("$CDN_URL/uploads", CDN_URL)
        url.startsWith("$baseUrl/uploads") -> url.replace("$baseUrl/uploads", CDN_URL)
        else -> url
    }
}
