package eu.kanade.tachiyomi.extension.uk.pureskill

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
class SearchResponse(
    val titles: List<TitlesList>,
)

@Serializable
class TitlesList(
    private val id: String,
    val title: String,
    private val coverPath: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id
        title = this@TitlesList.title
        thumbnail_url = "$baseUrl/media/titles/$coverPath"
    }
}

@Serializable
class MangaFull(
    private val title: String,
    private val description: String,
    private val artist: String,
    private val author: String,
    private val cover: String,
    private val chapters: Map<String, ChapterDto>,
) {
    fun toSManga(mangaUrl: String) = SManga.create().apply {
        url = mangaUrl
        title = this@MangaFull.title
        thumbnail_url = cover
        description = this@MangaFull.description.takeIf { it.isNotBlank() }
        artist = this@MangaFull.artist.takeIf { it.isNotBlank() }
        author = this@MangaFull.author.takeIf { it.isNotBlank() }
    }

    fun toSChapters(mangaUrl: String) = chapters.map {
        SChapter.create().apply {
            val prefix = if (it.value.volume.isBlank()) "" else "Том ${it.value.volume} "
            val suffix = if (it.value.title.isBlank()) "" else " ${it.value.title}"
            url = it.key
            name = "${prefix}Глава ${it.key}$suffix"
            chapter_number = it.key.toFloatOrNull() ?: -1f
            date_upload = it.value.lastUpdated.toLongOrNull()?.times(1000L) ?: 0L
            memo = buildJsonObject {
                put("pages", it.value.groups.sub.toJsonElement())
                put("mangaId", mangaUrl)
            }
        }
    }
}

@Serializable
class ChapterDto(
    val title: String,
    val volume: String,
    val groups: Pages,
    @SerialName("last_updated") val lastUpdated: String,
)

@Serializable
class Pages(
    @SerialName("Sub") val sub: List<String>,
)
