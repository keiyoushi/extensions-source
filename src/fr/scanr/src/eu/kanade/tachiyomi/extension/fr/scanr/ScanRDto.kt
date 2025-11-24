package eu.kanade.tachiyomi.extension.fr.scanr

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Objects for TeamScanR extension
 */

@Serializable
class Serie(
    val slug: String,
    val title: String,
    val description: String,
    val artist: String,
    val author: String,
    val cover: String,
    val os: Boolean = false,
    val chapters: Map<String, Chapter>,
    val completed: Boolean = false,
    val konami: Boolean = false,
)

@Serializable
class Chapter(
    val title: String,
    val volume: String,
    @SerialName("last_updated")
    val lastUpdated: String,
    val groups: Map<String, String>,
)

// DTO to SManga extension functions
fun Serie.toDetailedSManga(): SManga = SManga.create().apply {
    title = (if (this@toDetailedSManga.konami == true) "[+18] " else "") + this@toDetailedSManga.title
    description = this@toDetailedSManga.description
    artist = this@toDetailedSManga.artist
    author = this@toDetailedSManga.author
    status = if (this@toDetailedSManga.os || this@toDetailedSManga.completed) SManga.COMPLETED else SManga.ONGOING
    thumbnail_url = this@toDetailedSManga.cover
    url = "/${this@toDetailedSManga.slug}"
}
