package eu.kanade.tachiyomi.extension.all.batoto

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Data<T>(
    val data: T,
)

@Serializable
class Items<T>(
    private val paging: Paging,
    val items: List<T>,
) {
    @Serializable
    class Paging(
        val next: Int,
    )

    fun hasNextPage() = paging.next != 0
}

@Serializable
class ComicNode(
    private val id: String,
    private val name: String,
    private val altNames: List<String>? = null,
    private val authors: List<String>? = null,
    private val artists: List<String>? = null,
    private val originalStatus: String? = null,
    private val uploadStatus: String? = null,
    private val genres: List<String>? = null,
    private val summary: String? = null,
    private val extraInfo: String? = null,
    private val urlCoverOri: String? = null,
) {
    fun toSManga(baseUrl: String, cleanTitle: (String) -> String): SManga = SManga.create().apply {
        url = id
        title = cleanTitle(name)
        author = authors?.joinToString()
        artist = artists?.joinToString()
        genre = genres?.joinToString { genre ->
            genreOptions.firstOrNull { it.second == genre }?.first ?: genre
        }
        status = run {
            val statusToCheck = originalStatus ?: uploadStatus
            when {
                statusToCheck == null -> SManga.UNKNOWN
                statusToCheck.contains("pending") -> SManga.UNKNOWN
                statusToCheck.contains("ongoing") -> SManga.ONGOING
                statusToCheck.contains("cancelled") -> SManga.CANCELLED
                statusToCheck.contains("hiatus") -> SManga.ON_HIATUS
                statusToCheck.contains("completed") -> when {
                    uploadStatus?.contains("ongoing") == true -> SManga.PUBLISHING_FINISHED
                    else -> SManga.COMPLETED
                }
                else -> SManga.UNKNOWN
            }
        }
        thumbnail_url = urlCoverOri?.let { "$baseUrl$it" }
        description = buildString {
            if (!summary.isNullOrEmpty()) {
                append(summary)
            }
            if (!extraInfo.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\nExtra Info:\n")
                append(extraInfo)
            }
            if (!altNames.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative Titles:\n")
                append(altNames.joinToString("\n") { "- $it" })
            }
        }.replace(urlRegex, "<$1>")
        initialized = true
    }
}

private val urlRegex = Regex("""(https?://[^\s<"]+)""")

// ************ Comic Search ************ //

typealias ApiComicSearchResponse = Data<SearchData>

@Serializable
class SearchData(
    @SerialName("get_comic_browse")
    val response: Items<Data<ComicNode>>,
)

// ************ Manga Details ************ //

typealias ApiComicNodeResponse = Data<ComicNodeData>

@Serializable
class ComicNodeData(
    @SerialName("get_comicNode")
    val response: Data<ComicNode>,
)

// ************ Chapter List ************ //

typealias ApiChapterListResponse = Data<ChapterListData>

@Serializable
class ChapterListData(
    @SerialName("get_comic_chapterList")
    val response: List<Data<ChapterData>>,
) {
    @Serializable
    class ChapterData(
        val comicId: String,
        val id: String,
        val serial: Float? = null,
        @SerialName("dname")
        val displayName: String,
        val title: String? = null,
        val dateCreate: Long? = null,
        val dateModify: Long? = null,
        val userNode: Data<Name?>? = null,
        val groupNodes: List<Data<Name?>?>? = null,
    ) {
        @Serializable
        class Name(
            val name: String? = null,
        )

        fun toSChapter(): SChapter = SChapter.create().apply {
            url = id
            name = buildString {
                if (serial != null) {
                    val number = serial.toString().substringBefore(".0")
                    if (!displayName.contains(number)) {
                        append("Chapter ", number, ": ")
                    }
                }
                append(displayName)
                if (!title.isNullOrEmpty()) {
                    if (isNotEmpty()) append(": ")
                    append(title)
                }
            }
            serial?.let { chapter_number = it }
            date_upload = dateModify ?: dateCreate ?: 0L
            scanlator = groupNodes?.filter { it?.data?.name != null }?.joinToString { it!!.data!!.name!! }
                ?: userNode?.data?.name ?: "\u200B"
        }
    }
}

// ************ Chapter Pages ************ //

typealias ApiChapterNodeResponse = Data<ChapterNodeData>

@Serializable
class ChapterNodeData(
    @SerialName("get_chapterNode")
    val response: Data<ChapterData>,
) {
    @Serializable
    class ChapterData(
        val id: String,
        val comicId: String,
        val imageFile: ChapterImageFile,
    ) {
        @Serializable
        class ChapterImageFile(
            val urlList: List<String>,
        )
    }
}

// ************ My Updates ************ //

typealias ApiMyUpdatesResponse = Data<MyUpdatesData>

@Serializable
class MyUpdatesData(
    @SerialName("get_sser_myUpdates")
    val response: Items<Data<ComicNode>>,
)

// ************ My History ************ //

typealias ApiMyHistoryResponse = Data<MyHistoryData>

@Serializable
class MyHistoryData(
    @SerialName("get_sser_myHistory")
    val response: MyHistoryResult,
) {
    @Serializable
    class MyHistoryResult(
        val reqLimit: Int,
        val newStart: String,
        val items: List<MyHistoryItem>,
    ) {
        @Serializable
        class MyHistoryItem(
            val comicNode: Data<ComicNode>,
        )
    }
}

// ************ User\'s Publish Comic List ************ //

typealias ApiUserComicListResponse = Data<UserComicListData>

@Serializable
class UserComicListData(
    @SerialName("get_user_comicList")
    val response: Items<Data<ComicNode>>,
)
