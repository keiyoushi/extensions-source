package eu.kanade.tachiyomi.extension.zh.noyacg

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

const val LISTING_PAGE_SIZE = 20

@Serializable
class MangaDto(
    @SerialName("Bid") private val id: Int,
    @SerialName("Bookname") private val title: String,
    @SerialName("Author") private val author: String,
    @SerialName("Pname") private val character: String,
    @SerialName("Ptag") private val genres: String,
    @SerialName("Otag") private val parody: String,
    @SerialName("Time") private val timestamp: Long,
    @SerialName("Len") private val pageCount: Int,
) {
    fun toSManga(imageCdn: String) = SManga.create().also {
        it.url = id.toString()
        it.title = title
        it.author = author.formatNames()
        it.description = "时间：${mangaDateFormat.format(timestamp * 1000)}\n" +
            "页数：$pageCount\n" +
            "原作：${parody.formatNames()}\n" +
            "角色：${character.formatNames()}"
        it.genre = genres.replace(" ", ", ")
        it.status = SManga.COMPLETED
        it.thumbnail_url = "$imageCdn/$id/m1.webp"
        it.initialized = pageCount > 0
    }
}

fun SManga.field(index: Int): String =
    description!!.split("\n")[index].substringAfter('：')

val SManga.timestamp: Long get() = dateFormat.parse(field(0))!!.time
val SManga.pageCount: Int get() = field(1).toInt()

val dateFormat get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
private val mangaDateFormat = dateFormat

fun String.formatNames() = split(" ").joinToString { name ->
    name.split("-").joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}

@Serializable
class ListingPageDto(
    private val info: List<MangaDto>? = null,
    private val Info: List<MangaDto>? = null,
    val len: Int,
) {
    val entries get() = info ?: Info ?: emptyList()
}
