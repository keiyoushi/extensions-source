package eu.kanade.tachiyomi.extension.pt.funktoon

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class HomeDto(
    val releases: List<SerieDto>,
    private val sections: List<SectionDto>,
) {
    fun trending(): List<SerieDto> = sections.first { it.slug == "system_top_views" }.data.data
}

@Serializable
class SectionDto(
    val slug: String,
    val data: SectionDataDto,
)

@Serializable
class SectionDataDto(
    val data: List<SerieDto>,
)

@Serializable
class StampPageDto(
    val data: PaginatedDto,
)

@Serializable
class AuthorPageDto(
    val series: PaginatedDto,
)

@Serializable
class PaginatedDto(
    private val data: List<SerieDto>,
    @SerialName("next_page_url") private val nextPageUrl: String?,
) {
    fun toMangasPage() = MangasPage(data.map { it.toSManga() }, nextPageUrl != null)
}

@Serializable
class ShowcaseDto(
    private val showcase: List<ShowcaseItemDto>,
) {
    fun toMangasPage() = MangasPage(showcase.map { it.series.toSManga() }, false)
}

@Serializable
class ShowcaseItemDto(
    val series: SerieDto,
)

@Serializable
class SearchDto(
    private val data: SearchDataDto,
) {
    fun toMangasPage(): MangasPage {
        val series = data.groups.firstOrNull { it.kind == "series" } ?: return MangasPage(emptyList(), false)
        return MangasPage(series.data.map { it.toSManga() }, series.hasMore)
    }
}

@Serializable
class SearchDataDto(
    val groups: List<SearchGroupDto>,
)

@Serializable
class SearchGroupDto(
    val kind: String,
    @SerialName("has_more") val hasMore: Boolean,
    val data: List<SearchItemDto>,
)

@Serializable
class SearchItemDto(
    private val title: String,
    @SerialName("image_url") private val imageUrl: String?,
    @SerialName("target_url") private val targetUrl: String,
) {
    fun toSManga() = SManga.create().apply {
        url = targetUrl
        title = this@SearchItemDto.title.trim()
        thumbnail_url = imageUrl
    }
}

@Serializable
class StampListDto(
    val data: List<StampDto>,
)

@Serializable
class StampDto(
    val id: Int,
    val name: String,
)

@Serializable
class SerieDto(
    private val name: String,
    private val slug: String,
    private val cover: String?,
    private val thumb: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/serie/$slug"
        title = name.trim()
        thumbnail_url = cover ?: thumb
    }
}

@Serializable
class SerieDetailsDto(
    private val name: String,
    private val slug: String,
    private val desc: String?,
    private val cover: String?,
    private val thumb: String?,
    @SerialName("co_author") private val coAuthor: String?,
    private val author: AuthorDto?,
    private val stamp: StampDto?,
    private val genres: List<GenreDto>,
    private val comics: List<ComicDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = "/serie/$slug"
        title = name.trim()
        thumbnail_url = cover ?: thumb
        author = listOfNotNull(this@SerieDetailsDto.author?.name, coAuthor).joinToString().ifEmpty { null }
        genre = (genres.map { it.name } + listOfNotNull(stamp?.name)).distinct().joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(desc?.let { Jsoup.parseBodyFragment(it).body() })
            .build()
    }

    // Prose episodes (in the "Literatura" category) have no images.
    fun toChapterList(): List<SChapter> = comics
        .filter { it.status == "published" && it.imagesCount > 0 }
        .sortedBy { it.order }
        .mapIndexed { index, comic -> comic.toSChapter(slug, index + 1) }
        .reversed()
}

@Serializable
class AuthorDto(
    val name: String,
)

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ComicDto(
    private val id: Int,
    private val name: String,
    val order: Int,
    val status: String,
    @SerialName("images_count") val imagesCount: Int,
    @SerialName("created_at") private val createdAt: String?,
    private val monetization: MonetizationDto,
) {
    fun toSChapter(serieSlug: String, number: Int) = SChapter.create().apply {
        url = "/serie/$serieSlug/episodio/$id"
        name = buildString {
            if (!monetization.canAccess) append("🔒 ")
            append("Ep. $number · ${this@ComicDto.name.trim()}")
        }
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class MonetizationDto(
    @SerialName("can_access") val canAccess: Boolean,
)

@Serializable
class ReaderDto(
    private val data: List<ReaderPageDto>,
) {
    fun toPageList(): List<Page> = data.sortedBy { it.order }.mapIndexed { index, page -> Page(index, imageUrl = page.url) }
}

@Serializable
class ReaderPageDto(
    val order: Int,
    val url: String,
)
