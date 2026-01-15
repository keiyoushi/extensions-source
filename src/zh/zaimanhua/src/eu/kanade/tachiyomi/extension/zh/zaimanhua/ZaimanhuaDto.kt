package eu.kanade.tachiyomi.extension.zh.zaimanhua

import eu.kanade.tachiyomi.extension.zh.zaimanhua.Zaimanhua.Companion.DEFAULT_PAGE_SIZE
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.jsonPrimitive

@Serializable
class MangaDto(
    private val id: Int,
    private val title: String,
    private val cover: String?,
    private val description: String?,
    private val types: List<TagDto>?,
    private val status: List<TagDto>?,
    private val authors: List<TagDto>?,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@MangaDto.title
        author = authors?.joinToString { it.name }
        description = this@MangaDto.description
        genre = types?.joinToString { it.name }
        status = parseStatus(this@MangaDto.status?.firstOrNull()?.name.orEmpty())
        thumbnail_url = cover
        initialized = true
    }
}

@Serializable
class ChapterDataDto(
    private val id: Int,
    // Only `manhua.zaimanhua.com/api/v1/comic2/comic/detail?id=xxx`(pc) use `lastUpdateChapterId`,
    // `lastUpdateTime`, `chapterList`.
    // The app api use `last_update_chapter_id`, `last_updatetime`, `chapters`.
    @JsonNames("last_update_chapter_id")
    private val lastUpdateChapterId: Int,
    @JsonNames("last_updatetime")
    private val lastUpdateTime: Long,
    @JsonNames("chapters")
    val chapterList: List<ChapterGroupDto>?,
    val isHideChapter: Int?,
    val canRead: Boolean?,
) {
    fun parseChapterList(): List<SChapter> {
        val mangaId = id.toString()
        val lastUpdateChapter = lastUpdateChapterId.toString()
        val size = chapterList!!.sumOf { it.size }
        return chapterList.flatMapTo(ArrayList(size)) {
            it.toSChapterList(mangaId, lastUpdateChapter, lastUpdateTime)
        }
    }
}

@Serializable
class ChapterGroupDto(
    private val title: String,
    private val data: List<ChapterDto>,
) {
    fun toSChapterList(mangaId: String, lastUpdateChapter: String, lastUpdateTime: Long): List<SChapter> {
        val groupName = title
        val current = System.currentTimeMillis()
        return data.map {
            it.toSChapterInternal().apply {
                scanlator = groupName
                // For some chapters, api will always return current time as upload time
                // Therefore upload times that differ too little from the current time will be ignored
                // When the chapter is the latest chapter, use the last update time as the upload time
                if ((current - date_upload) < 10000) {
                    date_upload = if (url == lastUpdateChapter) {
                        lastUpdateTime * 1000
                    } else {
                        0L
                    }
                }
                url = "$mangaId/$url"
            }
        }
    }

    val size get() = data.size
}

@Serializable
class ChapterDto(
    @SerialName("chapter_id")
    private val id: Int,
    @SerialName("chapter_title")
    private val name: String,
    @SerialName("updatetime")
    private val updateTime: Long?,
) {
    fun toSChapterInternal() = SChapter.create().apply {
        url = id.toString()
        name = this@ChapterDto.name.formatChapterName()
        date_upload = updateTime?.times(1000) ?: 0L
    }
}

@Serializable
class ChapterImagesDto(
    @SerialName("page_url_hd")
    val images: List<String>,
    val canRead: Boolean,
)

@Serializable
class PageDto(
    // Only genre(/comic/filter/list) use `comicList`, others use `list`
    @JsonNames("comicList")
    private val list: List<PageItemDto>?,
    // Genre(/comic/filter/list) doesn't have `page` and `size`
    private val page: Int?,
    private val size: Int?,
    // Only genre(/comic/filter/list) use `totalNum`, others use `total`
    @JsonNames("totalNum")
    private val total: Int,
) {
    fun toMangasPage(page: Int): MangasPage {
        val currentPage = this.page ?: page
        val pageSize = this.size ?: DEFAULT_PAGE_SIZE
        if (list.isNullOrEmpty()) throw Exception("漫画结果为空，请检查输入")
        val hasNextPage = currentPage * pageSize < total
        return MangasPage(list.map { it.toSManga() }, hasNextPage)
    }
}

@Serializable
class PageItemDto(
    // must have at least one of id and comicId
    // Genre(/comic/filter/list) only have `id`
    // Ranking(/comic/rank/list) only have `comic_id`
    // latest(/comic/update/list) have both `id` (always 0) and `comic_id`
    // Search(/search/index) have both `id` and `comic_id` (always 0)
    private val id: Int?,
    @SerialName("comic_id")
    private val comicId: Int?,
    // Only genre(/comic/filter/list) use `name`, others use `title`
    @JsonNames("name")
    private val title: String,
    private val authors: String?,
    private val status: String?,
    private val cover: String?,
    private val types: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = (this@PageItemDto.comicId?.takeIf { it != 0 } ?: this@PageItemDto.id)!!.toString()
        title = this@PageItemDto.title
        author = authors?.formatList()
        genre = types?.formatList()
        status = parseStatus(this@PageItemDto.status ?: "")
        thumbnail_url = cover
    }
}

@Serializable
class TagDto(
    @SerialName("tag_name")
    val name: String,
)

@Serializable
class UserDto(
    @JsonNames("userInfo")
    val user: UserInfoDto?,
) {
    @Serializable
    class UserInfoDto(
        val token: String,
    )
}

@Serializable
class DataWrapperDto<T>(
    @JsonNames("comicInfo")
    val data: T?,
)

@Serializable
class SimpleResponseDto(
    val errno: Int? = 0,
)

@Serializable
class ResponseDto<T>(
    val errno: Int? = 0,
    val errmsg: String = "",
    val data: T,
)

@Serializable
data class ImageRetryParamsDto(
    val url: String,
    val index: Int,
)

@Serializable
class CanReadDto(
    val canRead: Boolean?,
)

@Serializable
class CommentDataDto(
    val list: List<JsonArray>?,
) {
    fun toCommentList(): List<String> {
        return if (list.isNullOrEmpty()) {
            listOf("没有吐槽")
        } else {
            list.map { item ->
                item.last().jsonPrimitive.content
            }
        }
    }
}

@Serializable
class JwtPayload(
    @SerialName("exp")
    val expirationTime: Long,
)
