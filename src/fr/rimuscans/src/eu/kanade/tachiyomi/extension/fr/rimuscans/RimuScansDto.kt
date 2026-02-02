package eu.kanade.tachiyomi.extension.fr.rimuscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class MangaListDto(
    val mangas: List<MangaDto>,
    val pagination: PaginationDto,
)

@Serializable
class MangaDetailsWrapperDto(
    val manga: MangaDto,
)

@Serializable
class MangaDto(
    val slug: String,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val description: String? = null,
    val cover: String,
    val status: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val type: String? = null,
    val genres: List<String> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/manga/$slug"
        title = this@MangaDto.title
        thumbnail_url = if (cover.startsWith("http")) cover else baseUrl + cover
        description = when {
            this@MangaDto.alternativeTitles.isEmpty() -> this@MangaDto.description
            this@MangaDto.description.isNullOrEmpty() -> "Alternative Titles:\n" + this@MangaDto.alternativeTitles.joinToString("\n")
            else -> this@MangaDto.description + "\n\nAlternative Titles:\n" + this@MangaDto.alternativeTitles.joinToString("\n")
        }
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        status = when (this@MangaDto.status?.lowercase()) {
            "ongoing", "en cours" -> SManga.ONGOING
            "completed", "terminé", "termine" -> SManga.COMPLETED
            "on hiatus", "hiatus", "en pause" -> SManga.ON_HIATUS
            "cancelled", "annulé", "annule" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        genre = (listOfNotNull(type) + genres).joinToString()
    }
}

@Serializable
class ChapterDto(
    val number: Double,
    val title: String? = null,
    val releaseDate: String? = null,
    val type: String? = null,
    val images: List<ImageDto> = emptyList(),
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val numberString = number.toString().substringBefore(".0")
        url = "/read/$mangaSlug/$numberString"
        name = when {
            title == null || title.isBlank() -> "Chapitre $numberString"
            title.contains("Chapitre", ignoreCase = true) || title.contains("Chapter", ignoreCase = true) -> title.trim()
            else -> "Chapitre $numberString : ${title.trim()}"
        }
        if (type == "PREMIUM") {
            name = "🔒 $name"
        }
        chapter_number = number.toFloat()
        scanlator = "Rimu Scans"
        date_upload = dateFormat.tryParse(releaseDate)
    }
}

@Serializable
class ImageDto(
    val order: Int,
    val url: String,
)

@Serializable
class PaginationDto(
    val hasNextPage: Boolean,
)
