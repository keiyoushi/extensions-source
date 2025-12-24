package eu.kanade.tachiyomi.extension.all.batotov4

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

// Search Comic Response
@Serializable
data class SearchComicResponse(
    val data: SearchComicData,
) {
    @Serializable
    data class SearchComicData(
        @SerialName("get_search_comic") val result: SearchComicResult,
    )

    @Serializable
    data class SearchComicResult(
        val req_page: Int? = null,
        val req_size: Int? = null,
        val req_word: String? = null,
        val new_page: Int? = null,
        val paging: PagingInfo,
        val items: List<SearchComicItem>,
    )

    @Serializable
    data class PagingInfo(
        val total: Int,
        val pages: Int,
        val page: Int,
        val init: Int? = null,
        val size: Int,
        val skip: Int? = null,
        val limit: Int? = null,
        val prev: Int? = null,
        val next: Int? = null, // 0 when unavailable
    )

    @Serializable
    data class SearchComicItem(
        val id: String? = null,
        val data: ComicData,
        val sser_follow: Boolean? = null,
        val sser_lastReadChap: LastReadChap? = null,
    )

    @Serializable
    data class ComicData(
        val id: String,
        val dbStatus: String? = null,
        val isPublic: Boolean? = null,
        val name: String,
        val origLang: String? = null,
        val tranLang: List<String>? = null,
        val urlPath: String,
        val urlCover600: String? = null,
        val urlCoverOri: String? = null,
        val genres: List<String>? = null,
        val altNames: List<String>? = null,
        val authors: List<String>? = null,
        val artists: List<String>? = null,
        val is_hot: Boolean? = null,
        val is_new: Boolean? = null,
        val sfw_result: String? = null,
        val score_val: Float? = null,
        val follows: Int? = null,
        val reviews: Int? = null,
        val comments_total: Int? = null,
        val chapterNode_up_to: ChapterNode? = null,
    ) {
        fun toSManga(): SManga = SManga.create().apply {
            title = name.trim()
            url = urlPath
            author = authors?.joinToString { it.trim() }
            artist = artists?.joinToString { it.trim() }
            genre = genres?.joinToString { genre ->
                genre
                    .replace("_", " ")
                    .replaceFirstChar {
                        if (it.isLowerCase()) {
                            it.titlecase(Locale.ROOT)
                        } else {
                            it.toString()
                        }
                    }
            }
            thumbnail_url = urlCoverOri ?: urlCover600
            description = buildString {
                if (!altNames.isNullOrEmpty()) {
                    append("Alternative Names:\n")
                    altNames.forEach { append("• $it\n") }
                }
            }.trim().ifEmpty { null }
        }
    }

    @Serializable
    data class LastReadChap(
        val date: String? = null,
        val chapterNode: ChapterNode? = null,
    )

    @Serializable
    data class ChapterNode(
        val id: String? = null,
        val data: ChapterNodeData? = null,
    )

    @Serializable
    data class ChapterNodeData(
        val id: String,
        val dateCreate: String? = null,
        val dbStatus: String? = null,
        val isFinal: Boolean? = null,
        val sfw_result: String? = null,
        val dname: String? = null,
        val urlPath: String,
        val is_new: Boolean? = null,
        val userId: String? = null,
        val userNode: UserNode? = null,
    )

    @Serializable
    data class UserNode(
        val id: String? = null,
        val data: UserData? = null,
    )

    @Serializable
    data class UserData(
        val id: String,
        val name: String? = null,
        val uniq: String? = null,
        val avatarUrl: String? = null,
        val urlPath: String? = null,
    )
}

// Chapter List Response
@Serializable
data class ChapterListResponse(
    val data: ChapterListData,
) {
    @Serializable
    data class ChapterListData(
        @SerialName("get_comic_chapterList") val chapters: List<ChapterListItem>,
    )

    @Serializable
    data class ChapterListItem(
        val id: String? = null,
        val data: ChapterInfo,
    )

    @Serializable
    data class ChapterInfo(
        val id: String,
        val dname: String? = null,
        val title: String? = null,
        val urlPath: String,
    ) {
        fun toSChapter(): SChapter = SChapter.create().apply {
            url = urlPath
            name = buildString {
                if (!dname.isNullOrEmpty()) {
                    append(dname)
                }
                if (!title.isNullOrEmpty()) {
                    if (isNotEmpty()) append(": ")
                    append(title)
                }
            }.ifEmpty { "Chapter $id" }
            date_upload
        }
    }
}

// Chapter Node Response (for page images)
@Serializable
data class ChapterNodeResponse(
    val data: ChapterNodeData,
) {
    @Serializable
    data class ChapterNodeData(
        @SerialName("get_chapterNode") val chapterNode: ChapterNodeInfo,
    )

    @Serializable
    data class ChapterNodeInfo(
        val data: ChapterData,
    )

    @Serializable
    data class ChapterData(
        val imageFile: ImageFile,
    )

    @Serializable
    data class ImageFile(
        val urlList: List<String>,
    )
}

// Comic Browse Pager Response (for /comics search pagination)
@Serializable
data class ComicBrowsePagerResponse(
    val data: ComicBrowsePagerData,
) {
    @Serializable
    data class ComicBrowsePagerData(
        @SerialName("get_comic_browse_pager") val paging: SearchComicResponse.PagingInfo,
    )
}

// =============================================================================
// GraphQL Payload DTOs
// =============================================================================

@Serializable
data class GraphQL<T>(
    val variables: T,
    val query: String,
)

@Serializable
data class SearchPayload(
    val query: String? = null,
    val incGenres: List<String>? = null,
    val excGenres: List<String>? = null,
    val incTLangs: List<String>? = null,
    val incOLangs: List<String>? = null,
    val sortby: String? = null,
    val chapCount: String? = null,
    val siteStatus: String? = null,
    val page: Int,
    val size: Int,
)

@Serializable
data class SearchVariables(
    val select: SearchPayload,
)

@Serializable
data class ChapterListVariables(
    val comicId: String,
    val start: Int, // set to -1 to grab all chapters
)

@Serializable
data class ChapterNodeVariables(
    val id: String,
)
