package eu.kanade.tachiyomi.extension.ar.alkhariqun

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class InertiaPage<T>(val props: T)

@Serializable
class ComicsProps(
    val locale: String,
    val comics: Paginated,
    val categories: List<CategoryDto>,
)

@Serializable
class Paginated(
    val data: List<ComicListDto>,
    val meta: PaginationMeta,
)

@Serializable
class PaginationMeta(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class CategoryDto(
    private val id: Int,
    private val translations: Map<String, String>,
) {
    fun toCategory(locale: String) = Category(translations.getValue(locale), id.toString())
}

@Serializable
class ImageDto(val path: String)

@Serializable
class ComicListDto(
    val slug: String,
    private val title: String,
    private val author: String?,
    private val categories: String?,
    @SerialName("featured_image") private val featuredImage: ImageDto?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/comics/$slug"
        title = this@ComicListDto.title
        author = this@ComicListDto.author
        genre = categories
        thumbnail_url = featuredImage?.path
    }
}

@Serializable
class ComicProps(
    val locale: String,
    val comic: ComicDto,
    val youMayAlsoLike: List<RelatedDto>,
)

@Serializable
class RelatedDto(val slug: String)

@Serializable
class ComicDto(
    val slug: String,
    private val title: String,
    private val description: String?,
    private val author: String?,
    private val categories: List<ComicCategoryDto>,
    val chapters: List<ChapterDto>,
    @SerialName("featured_image") private val featuredImage: ImageDto?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/comics/$slug"
        title = this@ComicDto.title
        author = this@ComicDto.author
        description = this@ComicDto.description?.let {
            MarkdownDescription.Builder().description(Jsoup.parseBodyFragment(it).body()).build()
        }
        genre = categories.joinToString { it.name }
        thumbnail_url = featuredImage?.path
    }
}

@Serializable
class ComicCategoryDto(val name: String)

@Serializable
class ChapterDto(
    private val slug: String,
    private val title: String,
    private val number: Float,
    @SerialName("publish_at") private val publishAt: String?,
) {
    fun toSChapter(comicSlug: String, locale: String) = SChapter.create().apply {
        url = "/comics/$comicSlug/chapters/$slug"
        name = title
        chapter_number = number
        date_upload = dateFormats.getValue(locale).tryParseDate(publishAt, siteZone)
    }
}

@Serializable
class ChapterProps(val chapterImages: List<String>)

// Chapter dates follow the site language chosen in the session
private val dateFormats = mapOf(
    "en" to DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
    "ar" to DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("ar")),
)
private val siteZone = ZoneId.of("Africa/Casablanca")
