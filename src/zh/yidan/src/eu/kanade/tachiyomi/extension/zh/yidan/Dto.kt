package eu.kanade.tachiyomi.extension.zh.yidan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ComicFetchRequest(
    val column: String,
    val page: Int,
    val limit: Int,
)

@Serializable
class ComicDetailRequest(
    val comicId: String,
    val userId: String,
    val limit: Int = 5,
)

@Serializable
class ChapterContentRequest(
    val chapterId: String,
    val userId: String,
    val type: Int = 1,
)

@Serializable
class KeywordSearchRequest(
    val key: String,
    val type: Int = 1,
)

@Serializable
class FilterRequest(
    val page: Int,
    val limit: Int,
    val categoryId: Int,
    val orderType: Int,
    val overType: Int,
) {
    @SerialName("updated_recent")
    val updatedRecent: Int? = if (orderType == 3) {
        1
    } else {
        null
    }
}

@Serializable
class CommonResponse<T>(val result: T)

@Serializable
class RecordResult(val records: List<Record>, val total: Int)

@Serializable
class FilterResult(val list: List<Record>, val total: Int)

@Serializable
class Record(
    val id: Long,
    val novelTitle: String,
    val imgUrl: String,
)

@Serializable
class ComicInfoResult(val comic: Comic, val chapterList: List<Chapter>)

@Serializable
class Comic(
    val id: Long,
    val novelTitle: String,
    val author: String,
    val tags: String,
    val bigImgUrl: String,
    val introduction: String,
    val overType: Int,
)

@Serializable
class Chapter(
    val id: Long,
    val chapterName: String,
    val createTime: String,
)

@Serializable
class ChapterContentResult(val content: List<Content>)

@Serializable
class Content(val url: String)
