package eu.kanade.tachiyomi.extension.ar.mangaswat

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal class MangaDetailsDto(
    private val title: String,
    private val story: String? = null,
    private val author: JsonElement? = null,
    private val artist: JsonElement? = null,
    private val genres: List<GenreDto> = emptyList(),
    private val status: StatusDto,
    private val poster: PosterDto,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDetailsDto.title
        description = story
        author = this@MangaDetailsDto.author?.let {
            when (it) {
                is JsonPrimitive -> it.contentOrNull
                is JsonObject -> it["name"]?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }
        artist = this@MangaDetailsDto.artist?.let {
            when (it) {
                is JsonPrimitive -> it.contentOrNull
                is JsonObject -> it["name"]?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }
        genre = genres.joinToString { it.name }
        status = when (this@MangaDetailsDto.status.name.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = poster.mediumUrl
    }
}

@Serializable
internal class GenreDto(
    internal val name: String,
)

@Serializable
internal class StatusDto(
    internal val name: String,
)

@Serializable
internal class LatestUpdatesResponse(
    internal val results: List<LatestMangaDto> = emptyList(),
    private val next: String? = null,
) {
    fun hasNext(): Boolean = next != null
}

@Serializable
internal class LatestMangaDto(
    private val id: Int,
    private val slug: String,
    private val title: String,
    private val poster: PosterDto,
) {
    fun toSManga(): SManga = SManga.create().apply {
        this.title = this@LatestMangaDto.title
        this.thumbnail_url = poster.mediumUrl
        this.url = id.toString()
    }
}

@Serializable
internal class PosterDto(
    @SerialName("medium") internal val mediumUrl: String,
)

@Serializable
internal class ChapterListResponse(
    private val count: Int,
    internal val next: String? = null,
    internal val results: List<ChapterDto> = emptyList(),
)

@Serializable
internal class ChapterDto(
    private val id: Int,
    private val slug: String,
    private val chapter: String,
    @SerialName("created_at") private val createdAt: String,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        name = chapter
        date_upload = MangaSwat.apiDateFormat.tryParse(createdAt)
        url = "/chapters/$id/$slug/"
    }
}

@Serializable
internal class PageListResponse(
    internal val images: List<PageDto>,
)

@Serializable
internal class PageDto(
    internal val image: String,
    internal val order: Int,
)
