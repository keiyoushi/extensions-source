package eu.kanade.tachiyomi.extension.en.revivalscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    else -> SManga.UNKNOWN
}

@Serializable
class SeriesResponseDto(
    val series: List<SeriesDto>,
)

@Serializable
class SeriesDto(
    private val id: String,
    private val title: String,
    private val coverImage: String? = null,
    private val status: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        this.title = this@SeriesDto.title
        url = id
        thumbnail_url = coverImage?.let { "$baseUrl$it" }
        this.status = parseStatus(this@SeriesDto.status)
    }
}

@Serializable
class ManhwaResponseDto(
    val manhwa: ManhwaDto,
)

@Serializable
class ManhwaDto(
    private val id: String,
    private val title: String,
    private val coverImage: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val genres: List<String>? = null,
    private val status: String? = null,
    private val chapters: List<ChapterDto>? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        this.title = this@ManhwaDto.title
        url = id
        thumbnail_url = coverImage?.let { "$baseUrl$it" }
        this.description = this@ManhwaDto.description
        this.author = this@ManhwaDto.author
        this.artist = this@ManhwaDto.artist
        this.genre = genres?.joinToString(", ")
        this.status = parseStatus(this@ManhwaDto.status)
        initialized = true
    }

    fun toSChapterList(showPremium: Boolean): List<SChapter> = chapters
        ?.filter { showPremium || !it.isPremium }
        ?.map { it.toSChapter(id) }
        ?.sortedByDescending { it.chapter_number }
        ?: emptyList()
}

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Float,
    private val title: String? = null,
    private val releaseDate: String? = null,
    private val accessRoles: List<String>? = null,
) {
    val isPremium: Boolean
        get() = accessRoles != null && "reader" !in accessRoles

    fun toSChapter(seriesId: String) = SChapter.create().apply {
        url = "/read/$seriesId/$id"
        val chapterName = title ?: "Chapter ${number.toString().removeSuffix(".0")}"
        name = if (isPremium) "🔒 $chapterName" else chapterName
        chapter_number = number
        date_upload = dateFormat.tryParse(releaseDate)
    }
}

@Serializable
class PagesResponseDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val url: String,
)
