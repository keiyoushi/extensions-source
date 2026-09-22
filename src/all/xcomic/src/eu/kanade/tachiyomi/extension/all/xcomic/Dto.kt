package eu.kanade.tachiyomi.extension.all.xcomic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.stringOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset

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

@Serializable
class XComicName(val name: String? = null)

@Serializable
class DateYMD(
    val y: Int? = null,
    val m: Int? = null,
    val d: Int? = null,
) {
    override fun toString(): String = buildString {
        if (y != null) append(y)
        if (m != null) append("-", m.toString().padStart(2, '0'))
        if (d != null) append("-", d.toString().padStart(2, '0'))
    }
}

@Serializable
class XComicStrings(
    val code: String? = null,
    val html: String? = null,
    val text: String? = null,
)

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
    val id: String? = null,
    private val name: String? = null,
    private val subName: String? = null,
    private val slug: String? = null,
    val translatedLanguage: String? = null,
    internal val readDirection: String? = null,
    internal val originalPubFrom: DateYMD? = null,
    internal val originalPubTill: DateYMD? = null,
    internal val originalPubZone: String? = null,
    @SerialName("chaps_normal") private val chapsNormal: Int? = null,
    val uploadStatus: String? = null,
    private val dbStatus: String? = null,
    private val isPublic: Boolean? = null,
    @SerialName("is_hot") internal val isHot: Boolean? = null,
    @SerialName("is_new") internal val isNew: Boolean? = null,
    internal val tags: List<String>? = null,
    internal val summary: XComicStrings? = null,
    internal val extraInfo: XComicStrings? = null,
    internal val authorNodes: List<XComicData<XComicName?>>? = null,
    internal val artistNodes: List<XComicData<XComicName?>>? = null,
    internal val publishers: List<String>? = null,
    internal val publisherNodes: List<XComicData<XComicName?>>? = null,
    @SerialName("title_titleNode") private val titleNodeWrapper: XComicData<TitleNode>? = null,
) {
    val titleNode: TitleNode?
        get() = titleNodeWrapper?.data

    fun toChapterEdition(id: String, fallbackName: String): ChapterEdition = ChapterEdition(id, chapterSourceLabel(subName, name, fallbackName))
}

@Serializable
class ComicNodeData(
    @SerialName("get_comicNode")
    val response: XComicData<ComicNode>? = null,
)

// Slim edition lookup used for language resolution.
@Serializable
class ComicProbeData(
    private val name: String? = null,
    private val subName: String? = null,
    val translatedLanguage: String? = null,
    private val dbStatus: String? = null,
    private val isPublic: Boolean? = null,
    @SerialName("chaps_normal") val chapsNormal: Int? = null,
) {
    fun isLive() = isPublic != false && (dbStatus == null || dbStatus == "normal")

    fun toChapterEdition(id: String, fallbackName: String): ChapterEdition = ChapterEdition(id, chapterSourceLabel(subName, name, fallbackName))
}

class ChapterEdition(
    val id: String,
    val label: String,
)

private fun chapterSourceLabel(subName: String?, name: String?, fallbackName: String): String = subName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: fallbackName

@Serializable
class ComicProbeDataEnvelope(
    @SerialName("get_comicNode")
    val response: XComicData<ComicProbeData>? = null,
)

@Serializable
class TitleNodeEnvelope(
    @SerialName("get_title_titleNode")
    val response: XComicData<TitleNode>? = null,
)

@Serializable
class TitleTrackingSites(
    @SerialName("mangaupdates") val mangaUpdates: String? = null,
    @SerialName("myanimelist") val myAnimeList: Long? = null,
    @SerialName("animeplanet") val animePlanet: String? = null,
    @SerialName("anilist") val aniList: Long? = null,
    val kitsu: Long? = null,
    val mangabaka: Long? = null,
    val shikimori: String? = null,
)

@Serializable
class TitleNode(
    private val id: String,
    private val title: String,
    @SerialName("alt_titles") private val altTitles: List<String>? = null,
    @SerialName("native_title") private val nativeTitle: String? = null,
    @SerialName("romanized_title") private val romanizedTitle: String? = null,
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
    @SerialName("total_chapters") private val totalChapters: Int? = null,
    @SerialName("total_follows") private val totalFollows: Int? = null,
    @SerialName("total_reviews") private val totalReviews: Int? = null,
    @SerialName("total_comments") private val totalComments: Int? = null,
    @SerialName("vote_val") private val scoreVal: Float? = null,
    @SerialName("vote_users") private val voteUsers: Int? = null,
    @SerialName("chap_last_public_at") val chapLastPublicAt: Long? = null,
    @SerialName("is_merged") private val isMerged: Boolean? = null,
    @SerialName("merged_to") private val mergedTo: String? = null,
    @SerialName("comic_ids") internal val comicIds: List<String>? = null,
    @SerialName("tracking_sites") private val trackingSites: TitleTrackingSites? = null,
) {
    val mergedTitleId: String?
        get() = if (isMerged == true && !mergedTo.isNullOrBlank() && mergedTo != id) mergedTo else null

    fun toSManga(
        baseUrl: String,
        cleanTitle: (String) -> String,
        url: String? = null,
        comic: ComicNode? = null,
        sources: List<String>? = null,
    ): SManga = SManga.create().apply {
        this.url = url ?: id
        title = cleanTitle(this@TitleNode.title)

        author = (
            comic?.authorNodes?.mapNotNull { it.data?.name }?.takeIf { it.isNotEmpty() }
                ?: authors
            )?.takeIf { it.isNotEmpty() }?.joinToString()
        artist = (
            comic?.artistNodes?.mapNotNull { it.data?.name }?.takeIf { it.isNotEmpty() }
                ?: artists
            )?.takeIf { it.isNotEmpty() }?.joinToString()

        genre = buildSet {
            typeId?.let { add(it.toTitleCase()) }
            demographicIds?.forEach { add(it.toTitleCase()) }
            contentRatingId?.let { add(it.toTitleCase()) }
            genreIds?.forEach { add(it.toTitleCase()) }
            formatIds?.forEach { add(it.toTitleCase()) }
        }.joinToString()

        status = parseStatus(this@TitleNode.status, comic?.uploadStatus)

        thumbnail_url = (coverLocalUrl ?: coverUrl)?.toAbsoluteUrl(baseUrl)

        description = buildString {
            if (comic?.isHot == true) append("🔥 HOT ")
            if (comic?.isNew == true) append("✨ NEW")
            if (comic?.isHot == true || comic?.isNew == true) append("\n\n")

            val metadata = buildList {
                originalLanguage?.let { ol ->
                    val label = languages.firstOrNull { it.second == ol }?.first ?: ol
                    add("**Original**: $label")
                }
                comic?.translatedLanguage?.let { tl ->
                    val label = languages.firstOrNull { it.second == tl }?.first ?: tl
                    add("**Translated**: $label")
                }
                comic?.originalPubFrom?.let { from ->
                    val till = comic.originalPubTill?.toString() ?: "Ongoing"
                    add("**Publication**: $from - $till")
                } ?: year?.takeIf { it > 0 }?.let { add("**Released**: $it") }
                comic?.originalPubZone?.takeIf { it.isNotEmpty() }?.let { add("**Region**: $it") }

                comic?.readDirection?.let { dir ->
                    val directionValues = listOf(
                        "ttb" to "⬇️ Top To Bottom",
                        "rtl" to "⬅️ Right To Left",
                        "ltr" to "➡️ Left To Right",
                    )
                    val label = directionValues.firstOrNull { it.first == dir }?.second ?: dir
                    add("**Read Direction**: $label")
                }

                chapLastPublicAt?.takeIf { it > 0 }?.let {
                    val date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    add("**Updated**: $date")
                }
            }

            if (metadata.isNotEmpty()) {
                append(metadata.joinToString("\n"))
                append("\n\n")
            }

            val stats = buildList {
                scoreVal?.takeIf { it > 0 }?.let { add("**Score**: %.1f".format(it)) }
                voteUsers?.takeIf { it > 0 }?.let { add("**Votes**: $it") }
                totalFollows?.takeIf { it > 0 }?.let { add("**Follows**: $it") }
                totalReviews?.takeIf { it > 0 }?.let { add("**Reviews**: $it") }
                totalComments?.takeIf { it > 0 }?.let { add("**Comments**: $it") }
                totalChapters?.takeIf { it > 0 }?.let { add("**Chapters**: $it") }
            }

            if (stats.isNotEmpty()) {
                append("**Statistics**\n${stats.joinToString(" · ")}")
                append("\n\n")
            }

            if (metadata.isNotEmpty()) {
                append("\n\n---\n\n")
            }

            val summaryText = description ?: comic?.summary?.text
            if (!summaryText.isNullOrEmpty()) {
                append(summaryText.toMarkdownUrls())
            }

            val links = buildList {
                trackingSites?.mangaUpdates?.let { add("[MangaUpdates](https://www.mangaupdates.com/series/$it)") }
                trackingSites?.myAnimeList?.let { add("[MyAnimeList](https://myanimelist.net/manga/$it)") }
                trackingSites?.animePlanet?.let { add("[Anime-Planet](https://www.anime-planet.com/manga/$it)") }
                trackingSites?.aniList?.let { add("[AniList](https://anilist.co/manga/$it)") }
                trackingSites?.kitsu?.let { add("[Kitsu](https://kitsu.app/manga/$it)") }
                trackingSites?.mangabaka?.let { add("[Mangabaka](https://mangabaka.org/$it)") }
                trackingSites?.shikimori?.let { add("[Shikimori](https://shikimori.io/mangas/$it)") }
            }

            if (links.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("**External Links**:\n")
                append(links.joinToString("\n") { "- $it" })
            }

            val extras = buildList {
                sources?.takeIf { it.isNotEmpty() }?.let { add("**Sources**: ${it.joinToString()}") }

                val pubList = comic?.publisherNodes?.mapNotNull { it.data?.name }
                    ?.takeIf { it.isNotEmpty() } ?: comic?.publishers
                pubList?.takeIf { it.isNotEmpty() }?.let { add("**Publishers**: ${it.joinToString()}") }

                comic?.tags?.takeIf { it.isNotEmpty() }?.let { add("**Tags**: ${it.joinToString()}") }
            }

            if (extras.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(extras.joinToString("\n\n"))
            }

            val alternativeTitles = buildList {
                nativeTitle?.takeIf { it.isNotBlank() && it != title }?.let { add(it) }
                romanizedTitle?.takeIf { it.isNotBlank() && it != title }?.let { add(it) }
                altTitles.orEmpty().forEach { alt ->
                    if (alt.isNotBlank() && alt !in this) add(alt)
                }
            }
            if (alternativeTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("**Alternative Titles**:\n")
                append(alternativeTitles.joinToString("\n") { "- $it" })
            }

            val extraInfoText = comic?.extraInfo?.text
            if (!extraInfoText.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n**Extra Info**:\n")
                append(extraInfoText.toMarkdownUrls())
            }
        }

        memo = buildJsonObject {
            urlPath?.let { put("urlPath", it) }
        }

        initialized = this@TitleNode.status != null
    }
}

internal fun parseStatus(status: String?, uploadStatus: String? = null): Int {
    val lower = status?.lowercase() ?: return SManga.UNKNOWN
    return when {
        "pending" in lower -> SManga.UNKNOWN
        "releasing" in lower || "ongoing" in lower -> SManga.ONGOING
        "cancelled" in lower -> SManga.CANCELLED
        "hiatus" in lower -> SManga.ON_HIATUS
        "completed" in lower -> when {
            uploadStatus?.contains("ongoing") == true -> SManga.PUBLISHING_FINISHED
            else -> SManga.COMPLETED
        }
        else -> SManga.UNKNOWN
    }
}

// ========================= Chapters List ============================

@Serializable
class ChapterListData(
    @SerialName("get_comic_chapterList_fullList")
    val response: ChapterListItems,
)

@Serializable
class ChapterListUniqData(
    @SerialName("get_comic_chapterList_uniqList")
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
    private val dbStatus: String? = null,
    private val isFinal: Boolean? = null,
    private val volume: JsonElement? = null,
    private val serial: Float? = null,
    @SerialName("dname")
    private val displayName: String = "",
    private val title: String? = null,
    private val urlPath: String? = null,
    // the API returns arbitrary JSON shapes for these depending on the chapter
    @SerialName("sfw_result")
    private val sfwResult: JsonElement? = null,
    @SerialName("chaDuplications")
    private val chaDuplications: JsonElement? = null,
    private val dateCreate: Long? = null,
    private val datePublic: Long? = null,
    private val dateModify: Long? = null,
    private val chaNum: Float? = null,
    private val volNum: Float? = null,
    private val volIdx: JsonElement? = null,
    private val count_images: Int? = null,
    @SerialName("is_new")
    private val isNew: Boolean? = null,
    @SerialName("srcName")
    private val srcName: String? = null,
    @SerialName("srcTitle")
    private val srcTitle: String? = null,
    @SerialName("srcColor")
    private val srcColor: String? = null,
    @SerialName("comments_topic")
    private val commentsTopic: Int? = null,
    @SerialName("comments_total")
    private val commentsTotal: Int? = null,
    @SerialName("views_login")
    private val viewsLogin: Int? = null,
    @SerialName("views_guest")
    private val viewsGuest: Int? = null,
    private val profileNodes: List<XComicData<XComicName?>?>? = null,
) {
    fun toSChapter(comicId: String, sourceLabel: String): SChapter = SChapter.create().apply {
        url = id
        val uploader = srcName?.takeIf { it.isNotEmpty() }?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        } ?: profileNodes?.mapNotNull { it?.data?.name }?.joinToString().takeIf { !it.isNullOrEmpty() }

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
            uploader?.let { append(" [", it, "]") }
        }

        memo = buildJsonObject {
            urlPath?.let { put("urlPath", it) }
            put(CHAPTER_COMIC_ID_MEMO, comicId)
            uploader?.let { put(CHAPTER_UPLOADER_MEMO, it) }
        }

        (chaNum ?: serial)?.let { chapter_number = it }
        date_upload = dateModify ?: dateCreate ?: datePublic ?: 0L

        scanlator = sourceLabel
    }
}

internal const val CHAPTER_COMIC_ID_MEMO = "comicId"
internal const val CHAPTER_UPLOADER_MEMO = "uploader"

internal fun SChapter.comicId(): String? = memo[CHAPTER_COMIC_ID_MEMO]?.stringOrNull

internal fun SChapter.uploader(): String? = memo[CHAPTER_UPLOADER_MEMO]?.stringOrNull

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
