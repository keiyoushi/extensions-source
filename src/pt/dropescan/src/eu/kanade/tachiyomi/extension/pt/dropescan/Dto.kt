package eu.kanade.tachiyomi.extension.pt.dropescan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class ObrasDto(
    val data: List<ObraDto>,
    val meta: MetaDto,
)

@Serializable
class MetaDto(
    val offset: Int,
    val total: Int,
)

@Serializable
class DataDto<T>(
    val data: T,
)

@Serializable
class RecentPageDto(
    val meta: RecentMetaDto,
    val data: List<ObraDto>,
)

@Serializable
class RecentMetaDto(
    @SerialName("CurrentPage") val currentPage: Int,
    @SerialName("LastPage") val lastPage: Int,
)

@Serializable
class ObraDto(
    @SerialName("ObraId") private val id: String,
    @SerialName("Title") private val title: String,
    @SerialName("OtherTitles") private val otherTitles: List<OtherTitleDto>? = null,
    @SerialName("Synopsis") private val synopsis: String? = null,
    @SerialName("PublicationYear") private val year: Int? = null,
    @SerialName("DemographicTarget") private val demographic: String? = null,
    @SerialName("Status") private val status: String? = null,
    @SerialName("Banners") private val banners: List<BannerDto>? = null,
    @SerialName("Authors") private val authors: List<AuthorDto>? = null,
    @SerialName("Artists") private val artists: List<ArtistDto>? = null,
    @SerialName("Formats") private val formats: List<FormatDto>? = null,
    @SerialName("Genres") private val genres: List<GenreDto>? = null,
    @SerialName("Themes") private val themes: List<ThemeDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/obras/$id"
        title = this@ObraDto.title
        thumbnail_url = (banners?.firstOrNull { it.isPrimary } ?: banners?.firstOrNull())?.let { imageUrl(it.path) }
        author = authors?.joinToString { it.name }?.ifEmpty { null }
        artist = artists?.joinToString { it.name }?.ifEmpty { null }
        genre = (formats.orEmpty().map { it.name } + genres.orEmpty().map { it.name } + themes.orEmpty().map { it.name })
            .joinToString().ifEmpty { null }
        status = when (this@ObraDto.status) {
            "Active" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            "Hiatus" -> SManga.ON_HIATUS
            "Cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .info(year?.toString(), demographic?.takeUnless { it == "Not Applicable" })
            .description(synopsis)
            .altTitles(otherTitles.orEmpty().mapNotNull { it.name }, "Títulos alternativos")
            .build()
    }
}

@Serializable
class OtherTitleDto(
    private val title: String? = null,
    @SerialName("Title") private val otherTitle: String? = null,
) {
    val name get() = title ?: otherTitle
}

@Serializable
class BannerDto(
    val isPrimary: Boolean = false,
    @SerialName("Variants") private val variants: VariantsDto? = null,
    @SerialName("SourceHigh") private val sourceHigh: String? = null,
    @SerialName("Source") private val source: String? = null,
) {
    val path get() = variants?.high?.source ?: sourceHigh ?: source!!
}

@Serializable
class VariantsDto(
    val high: VariantDto? = null,
)

@Serializable
class VariantDto(
    val source: String,
)

@Serializable
class AuthorDto(
    @SerialName("AuthorName") val name: String,
)

@Serializable
class ArtistDto(
    @SerialName("ArtistName") val name: String,
)

@Serializable
class FormatDto(
    @SerialName("FormatName") val name: String,
)

@Serializable
class GenreDto(
    @SerialName("GenreName") val name: String,
)

@Serializable
class ThemeDto(
    @SerialName("ThemeName") val name: String,
)

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    @SerialName("ChapterId") private val id: String,
    @SerialName("ChapterNumber") private val number: String,
    @SerialName("ChapterName") private val name: String? = null,
    @SerialName("CreatedAt") private val createdAt: String? = null,
    @SerialName("Groups") private val groups: List<GroupDto>? = null,
) {
    fun toSChapter(obraId: String) = SChapter.create().apply {
        url = "/obras/$obraId/$id/1"
        name = buildString {
            append("Capítulo ").append(number)
            this@ChapterDto.name?.takeUnless { it.isBlank() || it == "Capítulo $number" }?.let { append(" - ").append(it) }
        }
        chapter_number = number.toFloatOrNull() ?: -1f
        scanlator = groups?.joinToString { it.name }?.ifEmpty { null }
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class GroupDto(
    @SerialName("Name") val name: String,
)

@Serializable
class ChapterDetailsDto(
    val chapter: ChapterContentDto,
)

@Serializable
class ChapterContentDto(
    @SerialName("ChapterContent") val pages: List<PageDto>,
)

@Serializable
class PageDto(
    private val variants: VariantsDto? = null,
    private val sourceHigh: String? = null,
    private val source: String? = null,
) {
    val path get() = variants?.high?.source ?: sourceHigh ?: source!!
}

@Serializable
class TagListDto<T>(
    val data: List<T>,
)

@Serializable
class FormatTagDto(
    @SerialName("format_id") val id: String,
    @SerialName("format_name") val name: String,
)

@Serializable
class GenreTagDto(
    @SerialName("genre_id") val id: String,
    @SerialName("genre_name") val name: String,
)

@Serializable
class ThemeTagDto(
    @SerialName("theme_id") val id: String,
    @SerialName("theme_name") val name: String,
)

@Serializable
class TagsData(
    val formats: List<Pair<String, String>>,
    val genres: List<Pair<String, String>>,
    val themes: List<Pair<String, String>>,
)

private const val CDN_URL = "https://bucket-1.dropescan.com/"

fun imageUrl(path: String) = CDN_URL.toHttpUrl().resolve(path)!!.toString()
