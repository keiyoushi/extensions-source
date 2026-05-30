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

// ----- /manga/{slug} JSON-LD (details) -----

@Serializable
class ComicSeriesLd(
    val name: String = "",
    val description: String = "",
    val image: String = "",
    val alternateName: List<String> = emptyList(),
    val author: PersonLd? = null,
    val illustrator: PersonLd? = null,
    val genre: List<String> = emptyList(),
) {
    fun toSManga(baseUrl: String, typeLabel: String?, statusLabel: String?) = SManga.create().apply {
        title = name
        thumbnail_url = image.toAbsoluteUrl(baseUrl)
        description = buildString {
            val desc = this@ComicSeriesLd.description.trim()
            if (desc.isNotEmpty()) append(desc)
            val alts = alternateName.filter { it.isNotBlank() }
            if (alts.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Titres alternatifs : ")
                append(alts.joinToString(", "))
            }
        }.ifEmpty { null }
        author = this@ComicSeriesLd.author?.name?.trim()?.takeIf { it.isNotEmpty() }
        artist = illustrator?.name?.trim()?.takeIf { it.isNotEmpty() }
        status = when (statusLabel?.lowercase()) {
            "en cours", "ongoing" -> SManga.ONGOING
            "terminé", "termine", "completed" -> SManga.COMPLETED
            "en pause", "hiatus", "on hiatus" -> SManga.ON_HIATUS
            "annulé", "annule", "abandonné", "abandonne", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        genre = (listOfNotNull(typeLabel.toTypeLabel()) + this@ComicSeriesLd.genre).joinToString(", ")
    }

    private fun String?.toTypeLabel(): String? = when (this?.lowercase()) {
        "webtoon", "manhwa" -> "Manhwa"
        "manhua" -> "Manhua"
        "manga" -> "Manga"
        else -> this
    }
}

@Serializable
class PersonLd(
    val name: String = "",
)

// ----- Next.js flight data: chapters (listing) & reader (pages) -----

@Serializable
class NextChapterDto(
    val number: Double,
    val title: String = "",
    val releaseDate: String? = null,
    val type: String = "NORMAL",
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
