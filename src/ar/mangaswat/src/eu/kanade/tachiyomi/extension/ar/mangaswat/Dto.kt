package eu.kanade.tachiyomi.extension.ar.mangaswat

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class MangaListDto(
    val results: List<MangaDto>,
    private val next: String? = null,
) {
    fun hasNext(): Boolean = next != null
}

@Serializable
class MangaDto(
    private val id: Int? = null,
    private val serie_id: Int? = null,
    private val title: String,
    private val poster: PosterDto,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = poster.medium
        url = (id ?: serie_id)!!.toString()
    }
}

@Serializable
internal class MangaDetailsDto(
    private val title: String,
    private val poster: PosterDto,
    private val genres: List<AttributesDto> = emptyList(),
    private val story: String? = null,
    private val author: AttributesDto? = null,
    private val artist: AttributesDto? = null,
    private val status: AttributesDto? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDetailsDto.title
        thumbnail_url = poster.medium
        description = story
        genre = genres.joinToString { it.name }
        author = this@MangaDetailsDto.author?.name
        artist = this@MangaDetailsDto.artist?.name
        status = when (this@MangaDetailsDto.status?.name) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterListDto(
    val results: List<ChapterDto>,
    val next: String? = null,
)

@Serializable
class PageListDto(
    val images: List<PageDto>,
)

@Serializable
class PosterDto(
    val medium: String,
)

@Serializable
class AttributesDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val id: Int,
    private val slug: String,
    private val chapter: String,
    @SerialName("created_at") private val createdAt: String,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        name = chapter
        url = "/chapters/$id/$slug/"
        date_upload = Instant.parseOrNull(createdAt)?.toEpochMilliseconds() ?: 0L

        // Not used
        memo = buildJsonObject {
            put("id", id)
            put("slug", slug)
        }
    }
}

@Serializable
class PageDto(
    val image: String,
)
