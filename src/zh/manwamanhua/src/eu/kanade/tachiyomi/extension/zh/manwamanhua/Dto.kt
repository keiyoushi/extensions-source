package eu.kanade.tachiyomi.extension.zh.manwamanhua

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.jsoup.parser.Parser

@Serializable
class ResponseDto<T>(val data: T)

@Serializable
class CateRequestDto(
    private val page: PageRequestDto,
    private val sort: Int,
    private val comic: ComicFilterDto,
)

@Serializable
class PageRequestDto(private val page: Int, private val pageSize: Int)

@Serializable
class ComicFilterDto(
    private val status: Int,
    private val day: Int,
    private val level: Int,
    private val areaId: Int,
)

@Serializable
class ListDto<T>(
    val total: Int,
    val list: List<T>,
)

@Serializable
class CateComicDto(
    private val url: String,
    private val title: String,
    private val pic: String,
    private val author: String,
    private val tags: String,
) {
    fun toSManga() = listingManga(url.substringAfterLast('/'), title, pic, author, tags)
}

@Serializable
class ComicItemDto(
    private val id: Int,
    private val title: String,
    @JsonNames("pic") private val cover: String,
    private val author: String,
    private val tags: String,
) {
    fun toSManga() = listingManga(id.toString(), title, cover, author, tags)
}

private fun listingManga(id: String, title: String, cover: String, author: String, tags: String) = SManga.create().apply {
    url = id
    this.title = title.unescape()
    thumbnail_url = cover
    this.author = author.toAuthors()
    genre = tags.toTagList().joinToString().ifEmpty { null }
}

@Serializable
class ComicDto(
    private val id: Int,
    private val title: String,
    private val alias: String? = null,
    private val cover: String,
    private val status: Int,
    private val author: String? = null,
    private val tags: String? = null,
    private val intro: String? = null,
    private val areaId: Int? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@ComicDto.title.unescape()
        thumbnail_url = cover
        author = this@ComicDto.author?.toAuthors()
        val tagList = tags?.toTagList().orEmpty()
        genre = (listOfNotNull(areaId?.let(AREAS::get)?.takeIf { it !in tagList }) + tagList).joinToString().ifEmpty { null }
        status = when (this@ComicDto.status) {
            1 -> SManga.ONGOING
            0 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(intro?.unescape()?.replace("\r\n", "\n"))
            .altTitles(alias?.unescape()?.split('、').orEmpty(), "别名")
            .build()
        initialized = true
    }

    companion object {
        // Area ids under the names the site's tags use
        private val AREAS = mapOf(2 to "韩漫", 3 to "日漫", 4 to "国漫", 5 to "台漫")
    }
}

@Serializable
class ChapterListDto(val list: List<ChapterDto>)

@Serializable
class ChapterDto(
    val id: Int,
    val sortId: Float,
    private val comicId: Int,
    private val title: String,
    private val createTime: Long,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/comic/$comicId/$id"
        name = title.unescape()
        scanlator = TAIWAN_EDITION.takeIf { it in name }
        date_upload = createTime * 1000
    }
}

@Serializable
class ImagesDto(
    val images: List<ImageDto>,
    val pagination: PaginationDto,
)

@Serializable
class ImageDto(
    val url: String,
    val width: Int,
)

@Serializable
class PaginationDto(
    @SerialName("total_pages") val totalPages: Int,
)

const val TAIWAN_EDITION = "台版"

// The API returns HTML-escaped text
private fun String.unescape() = Parser.unescapeEntities(this, false)

private fun String.toAuthors() = unescape().split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }.joinToString().ifEmpty { null }

private fun String.toTagList() = unescape().split(',').filter { it.isNotBlank() }
