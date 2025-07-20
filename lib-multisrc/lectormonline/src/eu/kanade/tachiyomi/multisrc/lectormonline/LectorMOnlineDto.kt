package eu.kanade.tachiyomi.multisrc.lectormonline

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class ComicListDataDto(
    val comics: List<ComicDto>,
    private val page: Int,
    private val totalPages: Int,
) {
    fun hasNextPage() = page < totalPages
}

@Serializable
class ComicDto(
    private val slug: String,
    private val name: String,
    private val state: String?,
    private val urlCover: String,
    private val description: String?,
    private val author: String?,
    private val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = name.substringBeforeLast("-").trim()
        thumbnail_url = urlCover
        status = state.parseStatus()
    }

    fun toSMangaDetails() = SManga.create().apply {
        url = slug
        title = name.substringBeforeLast("-").trim()
        thumbnail_url = urlCover
        description = this@ComicDto.description
        status = state.parseStatus()
        author = this@ComicDto.author
    }

    fun getChapters(): List<SChapter> {
        return chapters.map { it.toSChapter(slug) }
    }

    private fun String?.parseStatus(): Int {
        return when (this?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }
}

val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class ChapterDto(
    private val number: JsonPrimitive,
    private val createdAt: String,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "$mangaSlug/$number"
        name = "Capítulo $number"
        date_upload = dateFormat.tryParse(createdAt)
    }
}

@Serializable
class ChapterPagesDataDto(
    val chapter: ChapterPagesDto,
)

@Serializable
class ChapterPagesDto(
    val urlImagesChapter: List<String> = emptyList(),
)

@Serializable
class GenreListDto(
    val genres: List<GenreDto>,
)

@Serializable
class GenreDto(
    val name: String,
)
