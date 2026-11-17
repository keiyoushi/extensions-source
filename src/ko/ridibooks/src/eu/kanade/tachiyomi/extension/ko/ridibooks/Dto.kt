package eu.kanade.tachiyomi.extension.ko.ridibooks

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
class ApiResult<T>(
    val success: Boolean,
    val data: T?,
    val message: String?,
    val error: ApiErrorDto?,
)

@Serializable
class ApiErrorDto(
    val code: String,
)

@Serializable
class CategoryBooksDto(
    val items: List<CategoryItemDto>,
    val pagination: PaginationDto,
)

@Serializable
class PaginationDto(
    val nextPage: String?,
)

@Serializable
class CategoryItemDto(
    private val book: CategoryBookDto,
) {
    fun toSManga() = book.toSManga()
}

@Serializable
class CategoryBookDto(
    private val bookId: String,
    private val title: String,
    private val serial: SerialDto?,
) {
    fun toSManga() = SManga.create().apply {
        url = serial?.serialId ?: bookId
        title = serial?.title ?: this@CategoryBookDto.title
        thumbnail_url = coverUrl(url)
    }
}

@Serializable
class SerialDto(
    val serialId: String,
    val title: String,
)

@Serializable
class SearchDto(
    val total: Int,
    val books: List<SearchBookDto>,
)

@Serializable
class SearchBookDto(
    @SerialName("b_id") private val bookId: String,
    private val title: String,
) {
    fun toSManga() = SManga.create().apply {
        url = bookId
        title = this@SearchBookDto.title
        thumbnail_url = coverUrl(bookId)
    }
}

@Serializable
class BookDto(
    private val id: String,
    private val title: BookTitleDto,
    private val thumbnail: ThumbnailDto?,
    private val authors: List<AuthorDto> = emptyList(),
    private val categories: List<NamedDto> = emptyList(),
    private val series: SeriesDto?,
) {
    fun toSManga(intro: String?) = SManga.create().apply {
        url = series?.id ?: id
        title = series?.property?.title ?: this@BookDto.title.main
        thumbnail_url = thumbnail?.xxlarge ?: coverUrl(url)
        author = authors.filter { it.role !in setOf("illustrator", "translator") }
            .joinToString { it.name }.ifEmpty { null }
        artist = authors.filter { it.role in setOf("illustrator", "comic_author") }
            .joinToString { it.name }.ifEmpty { null }
        genre = categories.joinToString { it.name }
        status = when (series?.property?.isSerialComplete) {
            null, true -> SManga.COMPLETED
            false -> SManga.ONGOING
        }
        description = MarkdownDescription.Builder()
            .description(intro)
            .build()
    }
}

@Serializable
class BookTitleDto(
    val main: String,
)

@Serializable
class ThumbnailDto(
    val xxlarge: String?,
)

@Serializable
class AuthorDto(
    val name: String,
    val role: String,
)

@Serializable
class NamedDto(
    val name: String,
)

@Serializable
class SeriesDto(
    val id: String,
    val property: SeriesPropertyDto?,
)

@Serializable
class SeriesPropertyDto(
    val title: String?,
    @SerialName("is_serial_complete") val isSerialComplete: Boolean?,
)

@Serializable
class DescriptionsWrapperDto(
    private val descriptions: DescriptionsDto?,
) {
    val intro get() = descriptions?.intro
}

@Serializable
class DescriptionsDto(
    val intro: String?,
)

@Serializable
class EpisodeVariables(
    private val id: String,
    private val context: EpisodeContext,
)

@Serializable
class EpisodeContext(
    private val bookId: String,
    private val pagination: EpisodePagination,
    private val deviceType: String,
    private val order: String,
    private val tabType: String,
)

@Serializable
class EpisodePagination(
    private val offset: Int,
    private val limit: Int,
)

@Serializable
class EpisodeResponseDto(
    private val riGrid: RiGridDto,
) {
    val cell get() = riGrid.cells.bookDetailHome.episodeBookListCell.cell
}

@Serializable
class RiGridDto(
    val cells: CellsDto,
)

@Serializable
class CellsDto(
    val bookDetailHome: BookDetailHomeDto,
)

@Serializable
class BookDetailHomeDto(
    val episodeBookListCell: EpisodeBookListCellDto,
)

@Serializable
class EpisodeBookListCellDto(
    val cell: EpisodeBookListDto?,
)

@Serializable
class EpisodeBookListDto(
    val books: List<EpisodeDto>,
    val pagination: EpisodePaginationDto?,
)

@Serializable
class EpisodePaginationDto(
    val nextPage: EpisodeNextPageDto?,
)

@Serializable
class EpisodeNextPageDto(
    val offset: Int,
)

@Serializable
class EpisodeDto(
    private val bookId: String,
    private val title: String,
    private val metadata: EpisodeMetadataDto?,
    private val price: EpisodePriceDto?,
) {
    fun toSChapter(seriesTitle: String) = SChapter.create().apply {
        url = bookId
        val episodeTitle = title.removePrefix(seriesTitle).trim().ifEmpty { title }
        val locked = (price?.sellingPrice ?: 0) > 0 && metadata?.ownership == null
        name = if (locked) "🔒 $episodeTitle" else episodeTitle
        date_upload = dateFormat.tryParseDate(metadata?.regDate, seoul)
    }
}

@Serializable
class EpisodeMetadataDto(
    val regDate: String?,
    val ownership: OwnershipDto?,
)

@Serializable
class OwnershipDto

@Serializable
class EpisodePriceDto(
    val sellingPrice: Int?,
)

@Serializable
class ViewerRequest(
    @SerialName("book_id") private val bookId: String,
)

@Serializable
class TokenRequest(
    @SerialName("grant_type") private val grantType: String,
)

@Serializable
class ViewerDto(
    val pages: List<ViewerPageDto>,
)

@Serializable
class ViewerPageDto(
    val src: String,
)

fun coverUrl(bookId: String) = "https://img.ridicdn.net/cover/$bookId/xxlarge"

private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val seoul = ZoneId.of("Asia/Seoul")
