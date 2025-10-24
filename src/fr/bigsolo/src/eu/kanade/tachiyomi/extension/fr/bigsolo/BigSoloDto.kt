package eu.kanade.tachiyomi.extension.fr.bigsolo

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Objects for BigSolo extension
 */

@Serializable
data class SeriesResponse(
    val series: List<Serie>,
    val os: List<Serie>,
    val reco: List<Serie>,
)

@Serializable
data class Serie(
    val slug: String = "",
    val title: String,
    val description: String,
    val artist: String,
    val author: String,
    val demographic: String,
    val magazine: String,
    @SerialName("release_year")
    val releaseYear: Int,
    val tags: List<String>,
    @SerialName("ja_title")
    val jaTitle: String,
    @SerialName("alternative_titles")
    val alternativeTitles: List<String>,
    val teams: List<Teams>,
    @SerialName("imgchest_username")
    val imgchestUsername: String,
    val status: String,
    val cover: Cover? = null,
    val chapters: Map<String, Chapter>,
    @SerialName("last_chapter")
    val lastChapter: lastChapter? = null,
)

@Serializable
data class lastChapter(
    val number: Int,
    val title: String,
    val volume: String = "",
    val timestamp: Int,
    val teams: List<String>,
)

@Serializable
data class Chapter(
    val title: String,
    val volume: String = "",
    val timestamp: Int,
    val teams: List<String>,
    val source: Source? = null,
    @SerialName("licensed")
    val licencied: Boolean = false,
)

@Serializable
data class ChapterDetails(
    val images: List<String>,
)

@Serializable
data class Source(
    val service: String,
    val id: String,
)

@Serializable
data class Cover(
    @SerialName("url_hq")
    val urlHq: String,
    @SerialName("url_lq")
    val urlLq: String,
)

@Serializable
data class Teams(
    val id: String,
    val active: Boolean,
)

// DTO to SManga extension functions
fun Serie.toDetailedSManga(): SManga = SManga.create().apply {
    title = this@toDetailedSManga.title
    description = this@toDetailedSManga.description
    artist = this@toDetailedSManga.artist
    author = this@toDetailedSManga.author
    genre = this@toDetailedSManga.tags.joinToString(", ")
    status = when (this@toDetailedSManga.status) {
        "En cours" -> SManga.ONGOING
        "Finis", "Fini" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
    thumbnail_url = this@toDetailedSManga.cover?.urlHq
    url = "/${this@toDetailedSManga.slug}"
}
