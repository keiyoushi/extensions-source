package eu.kanade.tachiyomi.extension.zh.vomic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@Serializable
class Result<T>(
    val data: T,
    private val code: Int? = null,
    private val message: String? = null,
) {
    val result
        get(): T {
            if (code != 200) throw Exception(message ?: "Request Failed ($code)")
            return data
        }
}

@Serializable
class Token(
    val token: String,
)

@Serializable
class LoginPayload(
    val identity: String,
    val password: String,
)

@Serializable
class Manga(
    private val id: Int,
    private val name: String,
    private val intro: String? = null,
    private val cover: String? = null,
    private val author: String? = null,
    private val process: String? = null,
    private val tags: String? = null,
    private val cat: List<Category> = emptyList(),
    private val tag: List<MangaTag> = emptyList(),
    @SerialName("likes_num") private val likes: Int = 0,
    @SerialName("view_num") private val views: Int = 0,
    @SerialName("chapter_list")
    private val chapterList: List<ChapterGroup> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = cover
        author = this@Manga.author

        genre = buildList {
            cat.mapTo(this) { it.name }
            tag.mapTo(this) { it.name }
            tags?.split(' ')?.filter(String::isNotBlank)?.let(::addAll)
        }.distinct().joinToString()

        status = when (process) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            append(" 👁️ ${"%,d".format(views)} - ❤️ ${"%,d".format(likes)}")
            if (!intro.isNullOrBlank()) append("\n\n$intro")
        }
        initialized = true
    }

    val chapters get() = chapterList.flatMap { it.values }
}

@Serializable
class Category(
    val id: Int,
    val name: String,
)

@Serializable
class MangaTag(
    val name: String,
)

@Serializable
class ChapterGroup(
    val values: List<Chapter> = emptyList(),
)

@Serializable
class Chapter(
    private val id: Int,
    private val name: String,
    private val sort: String,
    @SerialName("car_id") private val mangaId: Int,
    @SerialName("group_name") private val groupName: String? = null,
    @SerialName("updated_at") private val updatedAt: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = "$mangaId/$id"
        name = this@Chapter.name
        date_upload = dateFormat.tryParseDateTime(createdAt ?: updatedAt)
        scanlator = groupName
    }
}

@Serializable
class Pages(
    @SerialName("img_list")
    val images: List<Image> = emptyList(),
)

@Serializable
class Image(
    val url: String? = null,
)
