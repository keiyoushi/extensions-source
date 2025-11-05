package eu.kanade.tachiyomi.extension.vi.otruyen

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.collections.mapIndexed

@Serializable
class DataDto<T>(
    val data: T,
)

@Serializable
class ListingData(
    val items: List<EntriesData>,
    val params: ParamsListing,
)

@Serializable
class ParamsListing(
    val pagination: Pagination,
)

@Serializable
class Pagination(
    val totalItems: Int,
    val totalItemsPerPage: Int,
    val currentPage: Int,
)

@Serializable
class EntriesData(
    private val name: String,
    private val slug: String,
    @SerialName("thumb_url") private val thumbUrl: String?,
    private val category: List<Category> = emptyList(),

) {
    fun toSManga(imgUrl: String): SManga = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = thumbUrl?.let { "$imgUrl/$it" }
        genre = category.joinToString { it.name }
    }
}

@Serializable
class Category(
    val name: String,
)

@Serializable
class EntryData(
    val item: Entry,
)

@Serializable
class Entry(
    private val name: String,
    val slug: String,
    @SerialName("origin_name") private val originName: List<String>,
    private val content: String,
    private val status: String,
    @SerialName("thumb_url") private val thumbUrl: String?,
    private val author: List<String>,
    private val category: List<Category>,
    val chapters: List<ChapterDto>,
    val updatedAt: String,
) {
    fun toSManga(imgUrl: String): SManga = SManga.create().apply {
        val entry = this@Entry
        author = entry.author.joinToString()
        val altNames = originName.filter { it.isNotBlank() }
        val descText = Jsoup.parse(content).select("p").joinToString("\n") { it.wholeText() }
        description = buildString {
            if (altNames.isNotEmpty()) {
                append("Tên khác: ${altNames.joinToString()}\n\n")
            }
            append(descText)
        }
        genre = category.joinToString { it.name }
        title = name
        thumbnail_url = thumbUrl?.let { "$imgUrl/$it" }
        status = when (entry.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "coming_soon" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    @SerialName("server_data") val serverData: List<ChapterData>,
)

@Serializable
class ChapterData(
    @SerialName("chapter_name") private val chapterName: String,
    @SerialName("chapter_title") private val chapterTitle: String? = null,
    @SerialName("chapter_api_data") private val chapterApiData: String,
) {
    fun toSChapter(date: String, mangaUrl: String): SChapter = SChapter.create().apply {
        val chapterId = chapterApiData.substringAfterLast("/")
        name = "Chapter " + chapterName + (chapterTitle?.let { " : $it" } ?: "")
        date_upload = dateFormat.tryParse(date) // API has no date for chapter → temporarily use updatedAt of entry
        chapter_number = chapterName.toFloatOrNull() ?: 0f
        url = "$chapterId:$mangaUrl"
    }
}

@Serializable
class PageDto(
    @SerialName("domain_cdn") val domainCdn: String,
    private val item: PageItem,
) {
    fun toPage(): List<Page> {
        val url = "$domainCdn/${item.chapterPath}/"
        return item.chapterImage.mapIndexed { index, image ->
            Page(index, imageUrl = url + image.imageFile)
        }
    }
}

@Serializable
class PageItem(
    @SerialName("chapter_path") val chapterPath: String,
    @SerialName("chapter_image") val chapterImage: List<PageImage>,
)

@Serializable
class PageImage(
    @SerialName("image_file") val imageFile: String,
)

@Serializable
class GenresData(
    val items: List<GenreItem>,
)

@Serializable
class GenreItem(
    val slug: String,
    val name: String,
)
private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
