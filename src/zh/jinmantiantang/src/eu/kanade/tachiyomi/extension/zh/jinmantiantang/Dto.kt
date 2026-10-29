package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.jsoup.parser.Parser

@Serializable
class ApiResponse(
    val code: Int,
    val data: JsonElement? = null,
    val errorMsg: String? = null,
)

@Serializable
class ServerListDto(
    @SerialName("Server") val server: List<String>,
)

@Serializable
class AlbumListDto(
    val content: List<AlbumItemDto> = emptyList(),
    @SerialName("redirect_aid") val redirectAid: Int? = null,
)

@Serializable
class FavoriteListDto(
    val list: List<AlbumItemDto>,
    val total: String,
)

@Serializable
class AlbumItemDto(
    private val id: String,
    private val name: String,
    private val author: String? = null,
    private val category: CategoryDto? = null,
    @SerialName("category_sub") private val categorySub: CategoryDto? = null,
) {
    fun toSManga(coverHost: String) = SManga.create().apply {
        url = albumPath(id)
        title = Parser.unescapeEntities(name, false)
        author = this@AlbumItemDto.author?.takeIf { it.isNotBlank() }
        genre = listOfNotNull(category?.title, categorySub?.title).distinct().joinToString().ifEmpty { null }
        thumbnail_url = coverUrl(coverHost, id)
    }
}

@Serializable
class CategoryDto(
    val title: String? = null,
)

@Serializable
class AlbumDto(
    private val id: Int,
    private val name: String,
    private val author: List<String> = emptyList(),
    private val description: String? = null,
    private val tags: List<String> = emptyList(),
    private val works: List<String> = emptyList(),
    private val actors: List<String> = emptyList(),
    private val series: List<SeriesDto> = emptyList(),
    @SerialName("series_id") val seriesId: String,
    private val addtime: String,
    @SerialName("related_list") val relatedList: List<RelatedDto> = emptyList(),
) {
    fun toSManga(coverHost: String) = SManga.create().apply {
        url = albumPath(id.toString())
        title = Parser.unescapeEntities(name, false)
        author = this@AlbumDto.author.joinToString().ifEmpty { null }
        genre = tags.filterNot { it in ONGOING || it == COMPLETED }.joinToString().ifEmpty { null }
        status = when {
            tags.any { it in ONGOING } -> SManga.ONGOING
            COMPLETED in tags -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            this@AlbumDto.description?.trim()?.takeIf { it.isNotEmpty() }?.let(::append)
            listOf("作品" to works, "登场人物" to actors).forEach { (label, values) ->
                if (values.isEmpty()) return@forEach
                if (isNotEmpty()) append("\n\n")
                append("**$label:**")
                values.forEach { append("\n- ").append(it) }
            }
        }.ifEmpty { null }
        thumbnail_url = coverUrl(coverHost, id.toString())
    }

    // Albums without series are a single chapter read by the album id, stored with the site's trailing slash
    fun toSChapterList(): List<SChapter> {
        val updated = addtime.toLongOrNull()?.times(1000) ?: 0L
        if (series.isEmpty()) {
            return listOf(
                SChapter.create().apply {
                    url = "${chapterPath(id.toString())}/"
                    name = "单章节"
                    date_upload = updated
                },
            )
        }
        return series.asReversed().mapIndexed { index, chapter ->
            SChapter.create().apply {
                url = chapterPath(chapter.id)
                name = chapter.displayName()
                chapter.number?.let { chapter_number = it }
                if (index == 0) date_upload = updated
            }
        }
    }
}

@Serializable
class SeriesDto(
    val id: String,
    private val name: String,
    private val sort: String,
) {
    val number get() = sort.toFloatOrNull()

    // The app labels chapters 第{sort}话 {name}; some names already carry that prefix
    fun displayName(): String {
        val prefix = "第${sort}话"
        return when {
            name.isBlank() -> prefix
            name.startsWith("第") -> name
            else -> "$prefix $name"
        }
    }
}

@Serializable
class RelatedDto(
    private val id: String,
    private val name: String,
    private val author: String? = null,
) {
    fun toSManga(coverHost: String) = SManga.create().apply {
        url = albumPath(id)
        title = Parser.unescapeEntities(name, false)
        author = this@RelatedDto.author?.takeIf { it.isNotBlank() }
        thumbnail_url = coverUrl(coverHost, id)
    }
}

@Serializable
class ChapterImagesDto(
    val images: List<ImageDto>,
)

@Serializable
class ImageDto(
    val image: String,
)

// Logging in again on a session that is still in the AVS cookie answers without a jwttoken
@Serializable
class MemberDto(
    val uid: String,
    val jwttoken: String? = null,
)

@Serializable
class DailyDto(
    @SerialName("daily_id") val dailyId: Int,
)

@Serializable
class MessageDto(
    val msg: String,
)

@Serializable
class CategoriesDto(
    val categories: List<CategoryGroupDto>,
    val blocks: List<TagBlockDto>,
)

@Serializable
class CategoryGroupDto(
    val name: String,
    val slug: String = "",
    @SerialName("sub_categories") val subCategories: List<SubCategoryDto>? = null,
)

@Serializable
class SubCategoryDto(
    val name: String,
    val slug: String,
)

@Serializable
class FilterData(
    val categories: List<Pair<String, String>>,
    val tagGroups: List<Pair<String, List<String>>>,
)

@Serializable
class TagBlockDto(
    val title: String,
    val content: List<String>,
)

private val ONGOING = listOf("连载中", "连载")
private const val COMPLETED = "完结"

private fun albumPath(id: String) = "/album/$id/"

private fun chapterPath(id: String) = "/photo/$id"

private fun coverUrl(coverHost: String, id: String) = "$coverHost/media/albums/${id}_3x4.jpg"
