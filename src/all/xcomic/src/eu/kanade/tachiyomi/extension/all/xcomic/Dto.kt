package eu.kanade.tachiyomi.extension.all.xcomic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
class XComicData<T>(
    val data: T? = null,
)

@Serializable
class XComicPaging(
    val next: Int? = 0,
    val total: Int? = 0,
) {
    fun hasNextPage() = (next ?: 0) != 0
}

// ============================ Browse ================================

@Serializable
class TitleBrowseItem(
    private val id: String,
    private val title: String,
    private val status: String? = null,
    @SerialName("cover_url") private val coverUrl: String? = null,
    @SerialName("cover_local_url") private val coverLocalUrl: String? = null,
    private val urlPath: String? = null,
) {
    fun toSManga(baseUrl: String, cleanTitle: (String) -> String): SManga = SManga.create().apply {
        url = id
        title = cleanTitle(this@TitleBrowseItem.title)
        status = parseStatus(this@TitleBrowseItem.status)
        thumbnail_url = (coverLocalUrl ?: coverUrl)?.toAbsoluteUrl(baseUrl)
        memo = buildJsonObject {
            urlPath?.let { put("urlPath", it) }
        }
    }
}

@Serializable
class BrowseItemsData(
    @SerialName("get_title_browse_items")
    val items: List<XComicData<TitleBrowseItem>>,
)

@Serializable
class BrowsePagerData(
    @SerialName("get_title_browse_pager")
    val pager: XComicPaging,
)

// ============================ Details ===============================

// A comic is a per-language edition of a title; the nested title node
// holds the shared work metadata used for the details screen.
@Serializable
class ComicNode(
    val translatedLanguage: String? = null,
    @SerialName("title_titleNode") private val titleNodeWrapper: XComicData<TitleNode>? = null,
) {
    val titleNode: TitleNode?
        get() = titleNodeWrapper?.data
}

@Serializable
class ComicNodeData(
    @SerialName("get_comicNode")
    val response: XComicData<ComicNode>? = null,
)

@Serializable
class TitleNode(
    private val id: String,
    private val title: String,
    private val altTitles: List<String>? = null,
    private val authors: List<String>? = null,
    private val artists: List<String>? = null,
    private val year: Int? = null,
    private val status: String? = null,
    private val description: String? = null,
    @SerialName("original_language") private val originalLanguage: String? = null,
    @SerialName("content_rating_id") private val contentRatingId: String? = null,
    @SerialName("type_id") private val typeId: String? = null,
    @SerialName("demographic_ids") private val demographicIds: List<String>? = null,
    @SerialName("genre_ids") private val genreIds: List<String>? = null,
    @SerialName("format_ids") private val formatIds: List<String>? = null,
    @SerialName("cover_url") private val coverUrl: String? = null,
    @SerialName("cover_local_url") private val coverLocalUrl: String? = null,
    private val urlPath: String? = null,
) {
    fun toSManga(baseUrl: String, cleanTitle: (String) -> String, url: String? = null): SManga = SManga.create().apply {
        this.url = url ?: id
        title = cleanTitle(this@TitleNode.title)

        author = authors?.takeIf { it.isNotEmpty() }?.joinToString()
        artist = artists?.takeIf { it.isNotEmpty() }?.joinToString()

        genre = buildSet {
            typeId?.let { add(it.toTitleCase()) }
            demographicIds?.forEach { add(it.toTitleCase()) }
            contentRatingId?.let { add(it.toTitleCase()) }
            genreIds?.forEach { add(it.toTitleCase()) }
            formatIds?.forEach { add(it.toTitleCase()) }
        }.joinToString()

        status = parseStatus(this@TitleNode.status)

        thumbnail_url = (coverLocalUrl ?: coverUrl)?.toAbsoluteUrl(baseUrl)

        description = buildString {
            description?.takeIf { it.isNotEmpty() }?.let { append(it.toMarkdownUrls()) }
            if (!altTitles.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("**Alternative Titles**:\n")
                append(altTitles.joinToString("\n") { "- $it" })
            }
        }

        memo = buildJsonObject {
            urlPath?.let { put("urlPath", it) }
        }

        initialized = this@TitleNode.status != null
    }
}

internal fun parseStatus(status: String?): Int = when (status?.lowercase()) {
    null -> SManga.UNKNOWN
    "releasing", "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

// ========================= Chapters List ============================

@Serializable
class ChapterListData(
    @SerialName("get_comic_chapterList_fullList")
    val response: ChapterListItems,
)

@Serializable
class ChapterListItems(
    val paging: XComicPaging,
    val items: List<ApiChapterWrapper>,
)

@Serializable
class ApiChapterWrapper(
    val id: String,
    val data: ChapterData,
)

@Serializable
class ChapterData(
    private val id: String,
    private val chaNum: Float? = null,
    private val volNum: Float? = null,
    private val serial: Float? = null,
    @SerialName("dname") private val displayName: String = "",
    private val title: String? = null,
    private val urlPath: String? = null,
    private val dateCreate: Long? = null,
    private val datePublic: Long? = null,
    private val dateModify: Long? = null,
    @SerialName("srcName") private val srcName: String? = null,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = id
        name = buildString {
            val number = (chaNum ?: serial)?.toString()?.removeSuffix(".0")
            if (number != null && !displayName.contains(number)) {
                append("Chapter ", number)
            }
            if (displayName.isNotEmpty()) {
                if (isNotEmpty()) append(": ")
                append(displayName)
            }
            if (!title.isNullOrEmpty()) {
                if (isNotEmpty()) append(": ")
                append(title)
            }
        }

        memo = buildJsonObject {
            urlPath?.let { put("urlPath", it) }
        }

        (chaNum ?: serial)?.let { chapter_number = it }
        date_upload = dateModify ?: dateCreate ?: datePublic ?: 0L

        scanlator = srcName?.takeIf { it.isNotEmpty() }?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }
}

// ========================= Chapter Pages ============================

@Serializable
class ChapterPagesData(
    @SerialName("get_chapterNode")
    val response: ChapterNodeWithImages,
)

@Serializable
class ChapterNodeWithImages(
    val id: String,
    val data: ChapterImageUrls,
)

@Serializable
class ChapterImageUrls(
    val imageUrls: List<String> = emptyList(),
)

// ============================= Helpers ==============================

private fun String.toTitleCase(): String = replace("_", " ").split(" ").joinToString(" ") { word ->
    word.lowercase().replaceFirstChar {
        if (it.isLowerCase()) it.titlecase() else it.toString()
    }
}

private fun String.toAbsoluteUrl(baseUrl: String): String = if (startsWith("http")) this else "$baseUrl$this"

private val urlRegex = Regex("""(?<![\[(])(https?://[^\s<"]+)""")

private fun String.toMarkdownUrls(): String = replace(urlRegex) { match ->
    val url = match.value
    "[$url]($url)"
}
