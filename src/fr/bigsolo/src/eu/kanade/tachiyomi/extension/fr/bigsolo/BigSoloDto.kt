package eu.kanade.tachiyomi.extension.fr.bigsolo

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Objects for BigSolo extension
 */

@Serializable
data class ConfigResponse(
    @SerialName("LOCAL_SERIES_FILES")
    val localSeriesFiles: List<String>,
)

@Serializable
data class SeriesData(
    val title: String,
    val description: String?,
    val artist: String?,
    val author: String?,
    @SerialName("cover_low")
    val coverLow: String?,
    @SerialName("cover_hq")
    val coverHq: String?,
    val tags: List<String>?,
    @SerialName("release_status")
    val releaseStatus: String?,
    val chapters: Map<String, ChapterData>?,
)

@Serializable
data class ReaderData(
    val series: SeriesData,
)

@Serializable
data class ChapterData(
    val title: String?,
    val volume: String?,
    @SerialName("last_updated")
    val lastUpdated: Long?,
    val licencied: Boolean = false,
    val groups: Map<String, String>?,
)

@Serializable
data class PageData(
    val link: String,
)

// DTO to SManga extension functions
fun SeriesData.toSManga(): SManga = SManga.create().apply {
    title = this@toSManga.title
    artist = this@toSManga.artist
    author = this@toSManga.author
    thumbnail_url = this@toSManga.coverLow
    url = "/${toSlug(this@toSManga.title)}"
}

fun SeriesData.toDetailedSManga(): SManga = SManga.create().apply {
    title = this@toDetailedSManga.title
    description = this@toDetailedSManga.description
    artist = this@toDetailedSManga.artist
    author = this@toDetailedSManga.author
    genre = this@toDetailedSManga.tags?.joinToString(", ") ?: ""
    status = when (this@toDetailedSManga.releaseStatus) {
        "En cours" -> SManga.ONGOING
        "Finis", "Fini" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
    thumbnail_url = this@toDetailedSManga.coverHq
    url = "/${toSlug(this@toDetailedSManga.title)}"
}

// Utility function for slug generation
// URLs are manually calculated using a slugify function
fun toSlug(input: String?): String {
    if (input == null) return ""

    val accentsMap = mapOf(
        'à' to 'a', 'á' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a',
        'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
        'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i',
        'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
        'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c', 'ñ' to 'n',
    )

    return input
        .lowercase()
        .map { accentsMap[it] ?: it }
        .joinToString("")
        .replace("[^a-z0-9\\s-]".toRegex(), "")
        .replace("\\s+".toRegex(), "-")
        .replace("-+".toRegex(), "-")
        .trim('-')
}
