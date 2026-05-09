package eu.kanade.tachiyomi.extension.all.utifa

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
internal class ApiEnvelope<T>(
    val code: Int = 0,
    val message: String? = null,
    val data: T? = null,
)

@Serializable
internal class MangaListRequest(
    val pageNum: Int,
    val pageSize: Int,
    val keyword: String? = null,
    val orderBy: String? = null,
    val order: String? = null,
    val theme: String? = null,
    val grade: String? = null,
    val updateStatus: String? = null,
)

@Serializable
internal class MangaPageDto(
    val records: List<MangaItemDto> = emptyList(),
    val current: Int = 1,
    val pages: Int = 1,
)

@Serializable
internal class MangaItemDto(
    val id: Long? = null,
    val title: String? = null,
    private val subTitle: String? = null,
    private val cover: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val genres: List<String>? = null,
    private val grade: String? = null,
    private val language: String? = null,
    private val updateStatus: String? = null,
) {
    fun toSManga(baseUrl: String, initialized: Boolean = false): SManga? {
        val mangaId = id ?: return null
        val mangaTitle = title?.takeIf(String::isNotBlank) ?: return null
        return SManga.create().apply {
            url = "/manga/detail/$mangaId"
            title = mangaTitle
            author = this@MangaItemDto.author?.takeIf(String::isNotBlank)
            description = buildDescription()
            genre = genres.orEmpty().filter(String::isNotBlank).joinToString(", ").takeIf(String::isNotBlank)
            status = updateStatus.toStatus()
            thumbnail_url = cover.toAbsoluteUrl(baseUrl)
            this.initialized = initialized
        }
    }

    private fun buildDescription(): String? = listOfNotNull(
        subTitle?.takeIf(String::isNotBlank),
        description?.takeIf(String::isNotBlank),
        grade?.takeIf(String::isNotBlank)?.let { "Rating: $it" },
        language?.takeIf(String::isNotBlank)?.let { "Language: $it" },
    ).joinToString("\n").takeIf(String::isNotBlank)
}

@Serializable
internal class MangaDetailDto(
    val id: Long? = null,
    val title: String? = null,
    private val subTitle: String? = null,
    private val cover: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val genres: List<String>? = null,
    private val grade: String? = null,
    private val language: String? = null,
    private val updateStatus: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: String): SManga {
        val mangaId = id ?: error("Manga id is missing")
        val mangaTitle = title?.takeIf(String::isNotBlank) ?: error("Manga title is missing")
        return SManga.create().apply {
            url = "/manga/detail/$mangaId"
            title = mangaTitle
            author = this@MangaDetailDto.author?.takeIf(String::isNotBlank)
            artist = author
            description = buildDescription()
            genre = genres.orEmpty().filter(String::isNotBlank).joinToString(", ").takeIf(String::isNotBlank)
            status = updateStatus.toStatus()
            thumbnail_url = cover.toAbsoluteUrl(baseUrl)
            initialized = true
        }
    }

    private fun buildDescription(): String? = listOfNotNull(
        subTitle?.takeIf(String::isNotBlank),
        description?.takeIf(String::isNotBlank),
        grade?.takeIf(String::isNotBlank)?.let { "Rating: $it" },
        language?.takeIf(String::isNotBlank)?.let { "Language: $it" },
    ).joinToString("\n").takeIf(String::isNotBlank)
}

@Serializable
internal class ChapterDto(
    val id: Long? = null,
    private val title: String? = null,
    private val updateTime: String? = null,
) {
    fun toSChapter(index: Int): SChapter? {
        val chapterId = id ?: return null
        return SChapter.create().apply {
            url = "/manga/chapter/$chapterId"
            name = title?.takeIf(String::isNotBlank) ?: "Chapter $chapterId"
            chapter_number = (index + 1).toFloat()
            date_upload = updateTime.parseServerDate()
        }
    }
}

@Serializable
internal class ChapterDetailDto(
    val chapterList: List<ChapterDto> = emptyList(),
    val chapters: List<FileItemDto> = emptyList(),
)

@Serializable
internal class FileItemDto(
    private val name: String? = null,
    private val url: String? = null,
) {
    fun toPage(index: Int, baseUrl: String): Page? {
        val imageUrl = url.toAbsoluteUrl(baseUrl) ?: return null
        return Page(index, name.orEmpty(), imageUrl)
    }
}

@Serializable
internal class LoginDataDto(
    val token: String? = null,
)

private fun String?.toAbsoluteUrl(baseUrl: String): String? {
    val value = this?.takeIf(String::isNotBlank) ?: return null
    return when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("/api/") -> "$baseUrl$value"
        value.startsWith("/") -> "$baseUrl/api$value"
        else -> "$baseUrl/$value"
    }
}

private fun String?.toStatus(): Int = when (this) {
    "0" -> SManga.ONGOING
    "1" -> SManga.COMPLETED
    else -> SManga.UNKNOWN
}

private fun String?.parseServerDate(): Long {
    val value = this?.takeIf(String::isNotBlank) ?: return 0L
    val normalized = value.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
    return runCatching {
        synchronized(SERVER_DATE_FORMAT) {
            SERVER_DATE_FORMAT.parse(normalized)?.time ?: 0L
        }
    }.getOrDefault(0L)
}

private val SERVER_DATE_FORMAT by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
}
