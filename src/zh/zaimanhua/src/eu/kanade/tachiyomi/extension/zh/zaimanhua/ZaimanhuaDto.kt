package eu.kanade.tachiyomi.extension.zh.zaimanhua

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
class MangaDto(
    private val id: Int,
    private val title: String,
    private val cover: String,
    private val description: String? = null,
    private val types: List<TagDto>,
    private val status: List<TagDto>,
    private val authors: List<TagDto>,
    @SerialName("chapters")
    private val chapterGroups: List<ChapterGroupDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@MangaDto.title
        author = authors.joinToString { it.name }
        description = this@MangaDto.description
        genre = types.joinToString { it.name }
        status = parseStatus(this@MangaDto.status[0].name)
        thumbnail_url = cover
        initialized = true
    }

    fun parseChapterList(): List<SChapter> {
        val mangaId = id.toString()
        val size = chapterGroups.sumOf { it.size }
        return chapterGroups.flatMapTo(ArrayList(size)) {
            it.toSChapterList(mangaId)
        }
    }
}

@Serializable
class ChapterGroupDto(
    private val title: String,
    private val data: List<ChapterDto>,
) {
    fun toSChapterList(mangaId: String): List<SChapter> {
        val groupName = title
        val isDefaultGroup = groupName == "连载"
        val current = System.currentTimeMillis()
        return data.map {
            it.toSChapterInternal().apply {
                url = "$mangaId/$url"
                if (!isDefaultGroup) scanlator = groupName
                // For some chapters, api will always return current time as upload time
                // Therefore upload times that differ too little from the current time will be ignored
                if ((current - date_upload) < 10000) date_upload = 0
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
    private val updateTime: Long = 0,
) {
    fun toSChapterInternal() = SChapter.create().apply {
        url = id.toString()
        name = this@ChapterDto.name.formatChapterName()
        date_upload = updateTime * 1000
    }
}

@Serializable
class ChapterImagesDto(
    @SerialName("page_url_hd")
    val images: List<String>,
)

@Serializable
class PageDto(
    private val list: List<PageItemDto>?,
    private val page: Int,
    private val size: Int,
    private val total: Int,
) {
    fun toMangasPage(): MangasPage {
        if (list.isNullOrEmpty()) throw Exception("漫画结果为空，请检查输入")
        val hasNextPage = page * size < total
        return MangasPage(list.map { it.toSManga() }, hasNextPage)
    }
}

@Serializable
class PageItemDto(
    @JsonNames("comic_id")
    private val id: Int,
    private val title: String,
    private val authors: String = "",
    private val status: String,
    private val cover: String,
    private val types: String,
) {
    fun toSManga() = SManga.create().apply {
        url = this@PageItemDto.id.toString()
        title = this@PageItemDto.title
        author = authors.formatList()
        genre = types.formatList()
        status = parseStatus(this@PageItemDto.status)
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
    val data: T?,
)

@Serializable
class SimpleResponseDto(
    val errno: Int = 0,
)

@Serializable
class ResponseDto<T>(
    val errno: Int = 0,
    val errmsg: String = "",
    val data: T,
)

@Serializable
data class ImageRetryParamsDto(
    val url: String,
    val index: Int,
)
