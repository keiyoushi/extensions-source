package eu.kanade.tachiyomi.extension.fr.rimuscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

// ----- /api/series (listing) -----

@Serializable
class SeriesListDto(
    val series: List<SeriesEntryDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
class SeriesEntryDto(
    val slug: String,
    val title: String,
    @SerialName("cover_url") val coverUrl: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/manga/$slug"
        title = this@SeriesEntryDto.title
        thumbnail_url = coverUrl.toAbsoluteUrl(baseUrl)
    }
}

// ----- /api/admin/genres -----

@Serializable
class GenresDto(
    val genres: List<String> = emptyList(),
)

// ----- /api/manga?slug=X (details) -----

@Serializable
class MangaDetailsWrapperDto(
    val manga: MangaDto,
)

@Serializable
class MangaDto(
    val slug: String,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val description: String,
    val cover: String,
    val status: String,
    val author: String? = null,
    val artist: String? = null,
    val type: String,
    val genres: List<String> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/manga/$slug"
        title = this@MangaDto.title
        thumbnail_url = cover.toAbsoluteUrl(baseUrl)
        description = buildString {
            val desc = this@MangaDto.description.trim()
            if (desc.isNotEmpty()) append(desc)
            val alts = alternativeTitles.filter { it.isNotBlank() }
            if (alts.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Titres alternatifs : ")
                append(alts.joinToString(", "))
            }
        }.ifEmpty { null }
        author = this@MangaDto.author?.trim()?.takeIf { it.isNotEmpty() }
        artist = this@MangaDto.artist?.trim()?.takeIf { it.isNotEmpty() }
        status = when (this@MangaDto.status.lowercase()) {
            "ongoing", "en cours" -> SManga.ONGOING
            "completed", "terminé", "termine" -> SManga.COMPLETED
            "on hiatus", "hiatus", "en pause" -> SManga.ON_HIATUS
            "cancelled", "annulé", "annule" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        genre = (listOfNotNull(type.toTypeLabel()) + genres).joinToString(", ")
    }

    private fun String.toTypeLabel(): String? = when (lowercase()) {
        "webtoon" -> "Manhwa"
        "manga" -> "Manga"
        else -> null
    }
}

@Serializable
class ChapterDto(
    val number: Double,
    val title: String,
    val releaseDate: String,
    val type: String,
    val images: List<ImageDto> = emptyList(),
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val numberString = number.toString().substringBefore(".0")
        url = "/read/$mangaSlug/$numberString"
        val cleanTitle = title.trim()
        val base = "Chapitre $numberString"
        name = when {
            cleanTitle.isBlank() || cleanTitle == base -> base
            cleanTitle.contains("Chapitre", ignoreCase = true) ||
                cleanTitle.contains("Chapter", ignoreCase = true) -> cleanTitle
            else -> "$base : $cleanTitle"
        }
        if (type.equals("PREMIUM", ignoreCase = true)) {
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

internal fun String.toAbsoluteUrl(baseUrl: String): String = when {
    startsWith("http") -> this
    startsWith("/") -> baseUrl + this
    else -> "$baseUrl/$this"
}
