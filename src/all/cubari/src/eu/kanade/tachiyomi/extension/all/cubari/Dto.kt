package eu.kanade.tachiyomi.extension.all.cubari

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.string
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class HistoryEntryDto(
    private val title: String,
    private val url: String,
    val pinned: Boolean,
    private val artist: String? = null,
    private val author: String? = null,
    private val description: String? = null,
    private val coverUrl: String? = null,
    private val cover: String? = null,
) {
    fun matches(query: String) = title.contains(query, true)

    fun toSManga() = createManga(title, artist, author, description, coverUrl ?: cover, url)
}

@Serializable
class SeriesDto(
    private val title: String,
    private val artist: String? = null,
    private val author: String? = null,
    private val description: String? = null,
    private val coverUrl: String? = null,
    private val cover: String? = null,
    val groups: Map<String, String>,
    val chapters: Map<String, ChapterDto>,
) {
    fun toSManga(url: String) = createManga(title, artist, author, description, coverUrl ?: cover, url)
}

@Serializable
class ChapterDto(
    val volume: JsonPrimitive,
    val title: String?,
    val groups: Map<String, JsonElement>,
    @SerialName("release_date") val releaseDate: Map<String, JsonPrimitive>? = null,
)

fun JsonElement.pageSrc(): String = if (this is JsonObject) {
    this["src"]!!.string
} else {
    string
}

private fun createManga(
    title: String,
    artist: String?,
    author: String?,
    description: String?,
    cover: String?,
    url: String,
) = SManga.create().apply {
    this.title = title
    this.artist = artist ?: Cubari.ARTIST_FALLBACK
    this.author = author ?: Cubari.AUTHOR_FALLBACK
    this.description = description?.substringBefore("Tags: ") ?: Cubari.DESCRIPTION_FALLBACK
    genre = description?.let {
        if (it.contains("Tags: ")) {
            it.substringAfter("Tags: ")
        } else {
            ""
        }
    } ?: ""
    this.url = url
    thumbnail_url = cover ?: ""
}
