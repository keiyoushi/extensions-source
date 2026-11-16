package eu.kanade.tachiyomi.extension.ar.mangaswat

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class MangaListDto(
    val results: List<MangaDto>,
    val next: String? = null,
)

@Serializable
class MangaDto(
    private val id: Int? = null,
    @SerialName("serie_id") private val serieId: Int? = null,
    private val title: String,
    private val poster: PosterDto,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = poster.medium.toAppHost()
        url = (id ?: serieId)!!.toString()
    }
}

@Serializable
class MangaDetailsDto(
    private val id: Int,
    private val title: String,
    private val poster: PosterDto,
    private val alternative: String? = null,
    private val type: AttributesDto? = null,
    private val genres: List<AttributesDto> = emptyList(),
    private val story: String? = null,
    private val author: AttributesDto? = null,
    private val artist: AttributesDto? = null,
    private val status: StatusDto? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id.toString()
        title = this@MangaDetailsDto.title
        thumbnail_url = poster.medium.toAppHost()
        description = MarkdownDescription.Builder()
            .description(story)
            .altTitles(alternative?.split("|").orEmpty())
            .build()
        genre = (listOfNotNull(type) + genres).joinToString { it.name }
        author = this@MangaDetailsDto.author?.name
        artist = this@MangaDetailsDto.artist?.name
        status = when (this@MangaDetailsDto.status?.id) {
            79 -> SManga.ONGOING
            80 -> SManga.COMPLETED
            81 -> SManga.ON_HIATUS
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
    val serie: SerieRefDto,
    val images: List<PageDto>,
)

@Serializable
class SerieRefDto(
    val id: Int,
)

@Serializable
class PosterDto(
    val medium: String,
)

@Serializable
class StatusDto(
    val id: Int,
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
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class PageDto(
    val image: String,
    val order: Int,
)

@Serializable
class GenreDto(
    val id: Int,
    val name: String,
)

// Cached API responses can carry the website's host
fun String.toAppHost(): String = toHttpUrl().newBuilder().host(MangaSwat.APP_HOST).build().toString()
