package eu.kanade.tachiyomi.extension.fr.bigsolo

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Objects for BigSolo extension
 */

@Serializable
class SeriesResponse(
    val series: List<Serie>,
    val os: List<Serie>,
    val reco: List<Serie>,
)

@Serializable
class Serie(
    val slug: String = "",
    val title: String,
    val description: String,
    val artist: String,
    val author: String,
    val tags: List<String>,
    @SerialName("ja_title")
    val jaTitle: String,
    @SerialName("alternative_titles")
    val alternativeTitles: List<String>,
    val status: String,
    val cover: Cover? = null,
    val chapters: Map<String, Chapter>,
    @SerialName("last_chapter")
    val lastChapter: lastChapter? = null,
)

@Serializable
class lastChapter(
    val timestamp: Int,
)

@Serializable
class Chapter(
    val title: String,
    val volume: String = "",
    val timestamp: Int,
    val teams: List<String>,
    @SerialName("licensed")
    val licencied: Boolean = false,
)

@Serializable
class ChapterDetails(
    val images: List<String>,
)

@Serializable
class Cover(
    @SerialName("url_hq")
    val urlHq: String,
)

// DTO to SManga extension functions
fun Serie.toDetailedSManga(): SManga = SManga.create().apply {
    title = this@toDetailedSManga.title
    description = this@toDetailedSManga.description
    artist = this@toDetailedSManga.artist
    author = this@toDetailedSManga.author
    genre = this@toDetailedSManga.tags.joinToString()
    status = when (this@toDetailedSManga.status) {
        "En cours" -> SManga.ONGOING
        "Finis", "Fini" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
    thumbnail_url = this@toDetailedSManga.cover?.urlHq
    url = "/${this@toDetailedSManga.slug}"
}
