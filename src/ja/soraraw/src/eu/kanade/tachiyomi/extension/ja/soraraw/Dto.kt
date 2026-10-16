package eu.kanade.tachiyomi.extension.ja.soraraw

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class ConfigDto(
    val apiImage: String,
    @SerialName("m") val catalogPages: Int,
)

@Serializable
class RankingDto(
    val mangas: List<MangaDto>,
    val manga18s: List<MangaDto>,
)

@Serializable
class ListingDto(
    val results: List<MangaDto>,
    private val pagination: PaginationDto,
) {
    val hasNextPage get() = pagination.currentPage < pagination.totalPage
}

@Serializable
class PaginationDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("total_page") val totalPage: Int,
)

@Serializable
class MangaDto(
    private val name: String,
    private val slug: String,
    private val image: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = image?.let { "$THUMBNAIL_URL/$it" }
    }
}

@Serializable
class CatalogDto(val list: List<CatalogEntryDto>)

@Serializable
class CatalogEntryDto(
    val name: String,
    private val slug: String,
    @SerialName("alt_names") val altNames: String? = null,
    val author: String? = null,
    val views: Long,
    val genres: List<Int>,
    private val img: String? = null,
    @SerialName("is_adult") private val adult: String,
) {
    val isAdult get() = adult == "yes"

    fun toSManga() = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = img?.let { "$THUMBNAIL_URL/$it" }
    }
}

@Serializable
class GenreDto(
    val id: Int,
    val name: String,
    val slug: String,
    val total: Int,
    @SerialName("is_adult") private val adult: String,
) {
    val isAdult get() = adult == "yes"
}

@Serializable
class MangaPageDto(val manga: MangaDetailsDto)

@Serializable
class MangaDetailsDto(
    private val name: String,
    private val slug: String,
    private val author: String? = null,
    private val content: String? = null,
    private val type: String? = null,
    private val image: String? = null,
    private val names: List<NameDto> = emptyList(),
    private val genres: List<NameDto> = emptyList(),
    private val chapters: List<ChapterDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = name
        author = this@MangaDetailsDto.author?.takeUnless { it == "更新中" }
        status = when (type) {
            "incomplete" -> SManga.ONGOING
            "complete" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = genres.takeIf { it.isNotEmpty() }?.joinToString { it.name }
        thumbnail_url = image?.let { "$THUMBNAIL_URL/$it" }
        description = buildString {
            content?.parseAs<ContentDto>()?.blocks?.forEach { block ->
                block.data.text?.let { append(Jsoup.parseBodyFragment(it).text(), "\n\n") }
                block.data.items?.forEach { append("• ", Jsoup.parseBodyFragment(it).text(), "\n") }
            }
            if (names.isNotEmpty()) {
                append("別名: ", names.joinToString { it.name })
            }
        }.trim().ifEmpty { null }
    }

    fun toSChapterList() = chapters.map { it.toSChapter(slug) }
}

@Serializable
class NameDto(val name: String)

@Serializable
class ContentDto(val blocks: List<ContentBlockDto>)

@Serializable
class ContentBlockDto(val data: ContentDataDto)

@Serializable
class ContentDataDto(
    val text: String? = null,
    val items: List<String>? = null,
)

@Serializable
class ChapterDto(
    private val name: Float? = null,
    private val title: String? = null,
    @SerialName("published_at") private val publishedAt: String? = null,
    private val path: String,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "$mangaSlug/${path.removePrefix("$mangaSlug-")}"
        name = listOfNotNull(
            this@ChapterDto.name?.let { "第${it.toString().removeSuffix(".0")}話" },
            title,
        ).joinToString(" ")
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ChapterPageDto(val chapter: ChapterDataDto)

@Serializable
class ChapterDataDto(
    val id: Long,
    val mode: String,
    val token: String? = null,
    val uuid: String,
    @SerialName("manga_id") val mangaId: Long,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("_b") val imageHost: String,
)

@Serializable
class ManifestDto(val d: String)

@Serializable
class ManifestEntryDto(
    val order: Float,
    val b: String,
)

private const val THUMBNAIL_URL = "https://i.mangaraw.lat"
