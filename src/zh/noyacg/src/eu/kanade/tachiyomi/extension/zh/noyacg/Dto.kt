package eu.kanade.tachiyomi.extension.zh.noyacg

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

const val LISTING_PAGE_SIZE = 20
val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

fun String.formatNames() = split(" ").joinToString { name ->
    name.split("-").joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}

fun MangaDto.formatDescription() = "时间：${DATE_FORMAT.format(time * 1000)}\n" +
    "评分：$rating\n" +
    "原作：${otag.formatNames()}\n" +
    "角色：${pname.formatNames()}" + (description.takeIf(CharSequence::isNotEmpty)?.let { "\n---\n$it" } ?: "")

@Serializable
data class ListingPageDto(
    val info: List<MangaDto>?,
    val len: Int?,
    val status: String,
)

@Serializable
data class SearchPageDto(
    val count: Int?,
    val data: List<SearchMangaDto>?,
    val status: String,
)

data class MangaDetailDto(
    val status: String,
    val book: MangaDto?,
    val chapters: List<Pair<String, List<ChapterDto>>>?,
)

@Serializable
data class CategoryDto(val id: Int, val name: String)

@Serializable
data class ChapterDto(
    val id: Int,
    val name: String,
    val count: Int,
    val sort: Int,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class MangaDto(
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
    fun toSManga() = SManga.create().also { m ->
        m.url = id.toString()
        m.title = name
        m.author = author.formatNames()
        m.description = formatDescription()
        m.genre = tags.replace(" ", ", ")
        m.status = if (mode == 0 || status == 1) SManga.COMPLETED else SManga.ONGOING
        m.thumbnail_url = "https://img.noymanga.com/$id/m1.webp"
        m.initialized = mode == 0 || description.isNotEmpty()
    }
}

@Serializable
data class SearchMangaDto(
    val id: Int,
    val name: String,
    val description: String,
    val author: String,
    val tags: List<String>,
    val pname: List<String>,
    val otag: List<String>,
    val time: Long,
    val mode: Int,
    val status: Int,
    @SerialName("rating_sum") val rating: Float,
) {
    fun toSManga() = SManga.create().also { m ->
        m.url = id.toString()
        m.title = name
        m.author = author.formatNames()
        m.description = "时间：${DATE_FORMAT.format(time * 1000)}\n" +
            "评分：$rating\n" +
            "原作：${otag.joinToString()}\n" +
            "角色：${pname.joinToString()}" + (description.takeIf(CharSequence::isNotEmpty)?.let { "\n---\n$it" } ?: "")
        m.genre = tags.joinToString()
        m.status = if (mode == 0 || status == 1) SManga.COMPLETED else SManga.ONGOING
        m.thumbnail_url = "https://img.noymanga.com/$id/m1.webp"
        m.initialized = mode == 0 || description.isNotEmpty()
    }
}
