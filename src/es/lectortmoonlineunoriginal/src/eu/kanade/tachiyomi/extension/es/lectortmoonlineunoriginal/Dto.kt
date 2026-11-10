package eu.kanade.tachiyomi.extension.es.lectortmoonlineunoriginal

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class MangaListDto(
    val data: List<MangaDto>,
    private val meta: MetaDto,
) {
    val hasNextPage get() = meta.currentPage < meta.lastPage
}

@Serializable
class MetaDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class MangaDetailsDto(
    val data: MangaDto,
)

@Serializable
class MangaDto(
    val slug: String,
    private val title: String,
    private val cover: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val synopsis: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val genres: List<GenreDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = cover
        description = synopsis
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        genre = (listOfNotNull(type) + genres.map { it.name }).joinToString().ifEmpty { null }
        status = this@MangaDto.status.orEmpty().lowercase().let {
            when {
                "curso" in it -> SManga.ONGOING
                "finalizado" in it -> SManga.COMPLETED
                "pausado" in it -> SManga.ON_HIATUS
                "cancelado" in it -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }
}

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

@Serializable
class ChapterDto(
    private val number: String,
    private val slug: String,
    private val title: String? = null,
    @SerialName("published_at") private val publishedAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = slug
        name = title ?: "Capítulo $number"
        number.toFloatOrNull()?.let { chapter_number = it }
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ChapterPagesDto(
    val data: ChapterDataDto,
)

@Serializable
class ChapterDataDto(
    val pages: List<String>? = null,
    val manga: MangaRefDto,
)

@Serializable
class MangaRefDto(
    val slug: String,
)
