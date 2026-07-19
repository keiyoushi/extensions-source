package eu.kanade.tachiyomi.extension.zh.noyacg

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

const val LISTING_PAGE_SIZE = 20
val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

fun String.formatAuthors() = split(" ").joinToString { name ->
    name.split("-").joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}

fun String.formatNames() = split(" ").joinToString()

@Serializable
class ListingPageDto(
    val info: List<MangaDto>?,
    val len: Int?,
    val status: String,
)

@Serializable
class SearchPageDto(
    val count: Int?,
    val data: List<SearchMangaDto>?,
    val status: String,
)

class MangaDetailDto(
    val status: String,
    val book: MangaDto?,
    val recommend: List<RecommendMangaDto>?,
    val chapters: Map<String, List<ChapterDto>>?,
)

@Serializable
class CategoryDto(val id: Int, val name: String)

@Serializable
class ChapterDto(
    val id: Int,
    val name: String,
    val count: Int,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
class MangaDto(
    @SerialName("Bid") val id: Int,
    @SerialName("Mode") val mode: Int,
    @SerialName("Bookname") val name: String,
    @SerialName("Description") val description: String,
    @SerialName("Author") val author: String,
    @SerialName("Pname") val pname: String,
    @SerialName("Ptag") val tags: String,
    @SerialName("Otag") val otag: String,
    @SerialName("Time") val time: Long,
    @SerialName("Len") val len: Int,
    @SerialName("Status") val status: Int,
    @SerialName("RatingSUM") val rating: Float,
) {
    fun toSManga(imgBaseUrl: String) = SManga.create().also { m ->
        m.url = id.toString()
        m.title = name
        m.author = author.formatAuthors()
        m.description = formatDescription()
        m.genre = tags.replace(" ", ", ")
        m.status = if (mode == 0 || status == 1) SManga.COMPLETED else SManga.ONGOING
        m.thumbnail_url = "$imgBaseUrl/$id/m1.webp"
        m.initialized = mode == 0 || description.isNotEmpty()
    }

    fun formatDescription() = description.ifBlank {
        "時間：${DATE_FORMAT.format(time * 1000)}\n评分：$rating\n原作：${otag.formatNames()}\n角色：${pname.formatNames()}"
    }
}

@Serializable
class RecommendMangaDto(
    @SerialName("bid") val id: Int,
    @SerialName("mode") val mode: Boolean,
    @SerialName("bookname") val name: String,
    @SerialName("author") val author: String,
    @SerialName("tags") val tags: String,
    @SerialName("status") val status: Boolean,
) {
    fun toSManga() = SManga.create().also { m ->
        m.url = id.toString()
        m.title = name
        m.author = author.formatAuthors()
        m.genre = tags.replace(" ", ", ")
        m.status = if (!mode || status) SManga.COMPLETED else SManga.ONGOING
        m.thumbnail_url = "https://img.noymanga.com/$id/m1.webp"
        m.initialized = !mode
    }
}

@Serializable
class SearchMangaDto(
    private val id: Int,
    private val mode: Int,
    private val name: String,
    private val description: String,
    private val author: String,
    private val pname: List<String>,
    private val tags: List<String>,
    private val otag: List<String>,
    private val time: Long,
    private val status: Int,
    @SerialName("rating_sum") private val rating: Float,
) {
    fun toSManga() = SManga.create().also { m ->
        m.url = id.toString()
        m.title = name
        m.author = author.formatAuthors()
        m.description = "時間：${DATE_FORMAT.format(time * 1000)}\n" +
            "评分：$rating\n" +
            "原作：${otag.joinToString()}\n" +
            "角色：${pname.joinToString()}" + (description.takeIf(CharSequence::isNotEmpty)?.let { "\n---\n$it" } ?: "")
        m.genre = tags.joinToString()
        m.status = if (mode == 0 || status == 1) SManga.COMPLETED else SManga.ONGOING
        m.thumbnail_url = "https://img.noymanga.com/$id/m1.webp"
        m.initialized = mode == 0 || description.isNotEmpty()
    }
}
