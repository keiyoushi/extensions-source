package eu.kanade.tachiyomi.extension.fr.ortegascans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class MangaUrl(
    val slug: String,
    val id: String,
)

@Serializable
class ChapterUrl(
    val mangaSlug: String,
    val id: String,
    val number: String,
)

@Serializable
class SeriesResponse(
    val data: List<SeriesDto>,
    val hasMore: Boolean,
)

@Serializable
class SeriesDto(
    val id: String,
    val title: String,
    val slug: String,
    val coverImage: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = MangaUrl(slug, id).toJsonString()
        title = this@SeriesDto.title
        thumbnail_url = "$baseUrl/${coverImage.replace("storage/", "api/")}"
    }
}

@Serializable
class MangaDetailsDataDto(
    val manga: MangaDto,
)

@Serializable
class MangaDto(
    val id: String,
    val title: String,
    val slug: String,
    val description: String? = null,
    val coverImage: String,
    val status: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val alternativeNames: String? = null,
    val categories: List<CategoryDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = MangaUrl(slug, id).toJsonString()
        title = this@MangaDto.title
        thumbnail_url = "$baseUrl/${coverImage.replace("storage/", "api/")}"
        description = listOfNotNull(
            this@MangaDto.description,
            this@MangaDto.alternativeNames?.let { "Noms alternatifs : $it" },
        ).joinToString("\n\n")
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        status = parseStatus(this@MangaDto.status)
        genre = categories.joinToString { it.name }
    }
}

@Serializable
class CategoryDto(
    val name: String,
)

@Serializable
class ChapterListDataDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val id: String,
    val number: Float,
    val title: String? = null,
    val isPremium: Boolean = false,
    val createdAt: String,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val chapterNumber = number.toString().removeSuffix(".0")

        url = ChapterUrl(mangaSlug, id, chapterNumber).toJsonString()
        name = buildString {
            if (isPremium) append("🔒 ")
            append("Chapitre ")
            append(chapterNumber)
            if (title != null) {
                append(" - ")
                append(title)
            }
        }
        chapter_number = number
        date_upload = DATE_FORMAT.tryParse(createdAt.removePrefix($$"$D"))
    }
}

@Serializable
class PageListDto(
    val images: List<ImageDto>,
)

@Serializable
class ImageDto(
    val index: Int,
    val url: String,
)

private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
    "en cours", "ongoing" -> SManga.ONGOING
    "terminé", "complete" -> SManga.COMPLETED
    "en pause", "on hold" -> SManga.ON_HIATUS
    "annulé", "canceled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
