package eu.kanade.tachiyomi.extension.zh.noyacg

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat
import java.util.Locale

const val LISTING_PAGE_SIZE = 20
val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

@Serializable
class LoginResponseDto(val status: String)

fun String.formatAuthors() = split(" ").joinToString { name ->
    name.split("-").joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}

@Serializable
class ListingPageDto(
    val status: String,
    @JsonNames("info", "data") val data: List<MangaDto>,
    @JsonNames("len", "count") val count: Int,
)

class MangaDetailDto(
    val status: String,
    val book: MangaDto,
    val recommend: List<RecommendMangaDto>,
    val chapters: Map<String, List<ChapterDto>>,
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
    @JsonNames("Bid", "id") val id: Int,
    @JsonNames("Mode", "mode") val mode: Int,
    @JsonNames("Bookname", "name") val name: String,
    @JsonNames("Description", "description") val description: String,
    @JsonNames("Author", "author") val author: String,
    @JsonNames("Time", "time") val time: Long,
    @JsonNames("Status", "status") val status: Int,
    @JsonNames("RatingSUM", "rating_sum") val rating: Float,
    @SerialName("Len") val len: Int?,
    @SerialName("Pname") val pName: String?,
    @SerialName("Ptag") val pTag: String?,
    @SerialName("Otag") val oTag: String?,
    val pname: List<String>?,
    val tags: List<String>?,
    val otag: List<String>?,
) {
    fun toSManga(imgBaseUrl: String) = SManga.create().also { m ->
        m.url = id.toString()
        m.title = name
        m.author = author.formatAuthors()
        m.description = formatDescription()
        m.genre = pTag?.replace(" ", ", ") ?: tags?.joinToString()
        m.status = if (mode == 0 || status == 1) SManga.COMPLETED else SManga.ONGOING
        m.thumbnail_url = "$imgBaseUrl/$id/m1.webp"
        m.initialized = mode == 0 || description.isNotEmpty()
    }

    fun formatDescription() = description.ifBlank {
        "時間：${DATE_FORMAT.format(time * 1000)}\n评分：$rating\n" +
            "原作：${oTag?.formatNames() ?: otag?.joinToString()}\n" +
            "角色：${pName?.formatNames() ?: pname?.joinToString()}"
    }

    fun String.formatNames() = split(" ").joinToString()
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
    fun toSManga(imgBaseUrl: String) = SManga.create().also { m ->
        m.url = id.toString()
        m.title = name
        m.author = author.formatAuthors()
        m.genre = tags.replace(" ", ", ")
        m.status = if (!mode || status) SManga.COMPLETED else SManga.ONGOING
        m.thumbnail_url = "$imgBaseUrl/$id/m1.webp"
        m.initialized = !mode
    }
}
