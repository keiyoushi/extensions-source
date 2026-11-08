package eu.kanade.tachiyomi.extension.all.webcomics

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
class DataWrapper<T>(
    val code: Int,
    val data: T,
)

@Serializable
class BookBody(
    @SerialName("book_id") val bookId: String,
)

@Serializable
class ChapterListBody(
    @SerialName("book_id") val bookId: String,
    val page: Int,
    val size: Int,
    val sort: String,
)

@Serializable
class ChapterDetailBody(
    @SerialName("book_id") val bookId: String,
    @SerialName("chapter_id") val chapterId: String,
    val index: Int,
)

@Serializable
class SearchBody(
    val word: String,
    val page: Int,
    val size: Int,
)

@Serializable
class BookDto(
    val name: String,
    val cover: String,
    val category: List<String>,
    val author: String,
    val description: String,
    val status: String,
)

@Serializable
class ChapterListDto(
    val list: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_id") val chapterId: String,
    val index: Int,
    @SerialName("is_pay") val isPay: Boolean,
    val name: String,
    @SerialName("update_time") val updateTime: Long,
)

@Serializable
class ImageListDto(
    @SerialName("base_url") val baseUrl: String,
    val images: List<ImageDto>,
)

@Serializable
class ImageDto(
    val url: String,
)

@Serializable
class BookListDto(
    private val list: List<BookItemDto>,
    private val pagination: PaginationDto,
) {
    fun toMangasPage() = MangasPage(list.map { it.toSManga() }, pagination.next)
}

@Serializable
class BookItemDto(
    @SerialName("book_id") private val bookId: String,
    private val name: String,
    private val cover: String,
    // The listing page's Nuxt payload names it sub_title, the search API category
    @JsonNames("sub_title") private val category: List<String>,
) {
    fun toSManga() = SManga.create().apply {
        url = bookId
        title = name
        thumbnail_url = cover
        memo = buildJsonObject {
            put("slug", category.first().toSlug())
            put("name", name.toSlug())
        }
    }
}

@Serializable
class PaginationDto(
    val next: Boolean,
)

// How the site builds the slugs in its links
private fun String.toSlug(): String = lowercase().replace(NON_WORD_REGEX, "-").trim('-')

private val NON_WORD_REGEX = """[^\p{L}\p{N}_]+""".toRegex()
