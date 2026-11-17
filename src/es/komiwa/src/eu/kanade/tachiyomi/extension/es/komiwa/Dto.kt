package eu.kanade.tachiyomi.extension.es.komiwa

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ResponseDto<T>(val data: T)

@Serializable
class CatalogBodyDto(
    private val page: Int,
    private val limit: Int,
    private val sortBy: String,
    private val sortOrder: String,
    private val q: String? = null,
    private val genres: List<String>? = null,
    private val types: List<String>? = null,
    private val demographics: List<String>? = null,
    private val status: String? = null,
    private val ageRating: String? = null,
)

@Serializable
class CatalogDto(
    val items: List<MangaDto>,
    val hasMore: Boolean,
)

@Serializable
class MangaPageDto(
    val manga: MangaDto,
    val initialChapters: ChapterListDto,
)

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    private val cover: String? = null,
    private val synopsis: String? = null,
    private val ratingAvg: Double? = null,
    private val year: Int? = null,
    private val type: TaxonomyDto? = null,
    private val demography: TaxonomyDto? = null,
    private val status: TaxonomyDto? = null,
    private val altTitles: List<String>? = null,
    private val genres: List<TaxonomyDto>? = null,
    private val authors: List<TaxonomyDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = cover
    }

    fun toSMangaDetails() = toSManga().apply {
        author = authors?.joinToString { it.name }?.ifEmpty { null }
        genre = listOfNotNull(type?.name, demography?.name).plus(genres.orEmpty().map { it.name }).joinToString().ifEmpty { null }
        status = when (this@MangaDto.status?.slug) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(ratingAvg)
            .info(year?.toString())
            .description(synopsis)
            .altTitles(altTitles.orEmpty(), "Títulos alternativos")
            .build()
    }
}

@Serializable
class TaxonomyDto(
    val id: String? = null,
    val name: String,
    val slug: String? = null,
)

@Serializable
class FiltersDto(
    val genres: List<TaxonomyDto>,
    val types: List<TaxonomyDto>,
    val demographics: List<TaxonomyDto>,
    val statuses: List<TaxonomyDto>,
)

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>,
    val hasMore: Boolean,
)

@Serializable
class ChapterDto(
    val id: String,
    private val number: String,
    private val title: String? = null,
    private val publishedAt: String? = null,
    private val createdAt: String? = null,
    private val group: TaxonomyDto? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id
        name = title?.ifBlank { null } ?: "Capítulo $number"
        chapter_number = number.toFloatOrNull() ?: -1f
        scanlator = group?.name
        // The newest chapters can have no publication date yet
        date_upload = Instant.tryParse(publishedAt ?: createdAt)
    }
}

@Serializable
class ReaderDto(
    val pages: List<String>,
    val imageSigs: Map<String, String> = emptyMap(),
    val manga: ReaderMangaDto,
)

@Serializable
class ReaderChaptersDto(val allChapters: List<ChapterDto>)

@Serializable
class ReaderMangaDto(val slug: String)
