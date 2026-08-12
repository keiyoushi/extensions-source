package eu.kanade.tachiyomi.extension.all.xcomic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode

@Serializable
class XComicName(val name: String? = null)

@Serializable
class XComicData<T>(
    val data: T,
)

@Serializable
class XComicPaging(
    val next: Int? = 0,
    val total: Int? = 0,
) {
    fun hasNextPage() = (next ?: 0) != 0
}

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
class ComicTrackingSites(
    @SerialName("mangaupdates") val mangaUpdates: String? = null,
    @SerialName("myanimelist") val myAnimeList: String? = null,
    @SerialName("animeplanet") val animePlanet: String? = null,
    @SerialName("anilist") val aniList: String? = null,
    val kitsu: String? = null,
)

@Serializable
class ComicNode(
    private val id: String,
    private val name: String,
    private val altNames: List<String>? = null,
    private val authors: List<String>? = null,
    private val authorNodes: List<XComicData<XComicName?>>? = null,
    private val artists: List<String>? = null,
    private val artistNodes: List<XComicData<XComicName?>>? = null,
    private val originalLanguage: String? = null,
    private val translatedLanguage: String? = null,
    private val originalStatus: String? = null,
    private val originalPubFrom: DateYMD? = null,
    private val originalPubTill: DateYMD? = null,
    private val originalPubZone: String? = null,
    private val uploadStatus: String? = null,
    private val type: String? = null,
    private val demographics: List<String>? = null,
    private val contentRating: String? = null,
    private val genres: List<String>? = null,
    private val tags: List<String>? = null,
    private val publishers: List<String>? = null,
    private val publisherNodes: List<XComicData<XComicName?>>? = null,
    private val tagNodes: List<XComicData<XComicName?>>? = null,
    private val summary: XComicStrings? = null,
    private val extraInfo: XComicStrings? = null,
    private val urlPath: String? = null,
    private val urlCover: String? = null,
    @SerialName("is_hot")
    private val isHot: Boolean? = null,
    @SerialName("is_new")
    private val isNew: Boolean? = null,
    private val follows: Int? = null,
    private val reviews: Int? = null,
    @SerialName("comments_total")
    private val commentsTotal: Int? = null,
    @SerialName("score_val")
    private val scoreVal: Float? = null,
    @SerialName("chaps_normal")
    private val chapsNormal: Int? = null,
    private val trackingSites: ComicTrackingSites? = null,
) {
    fun toSManga(baseUrl: String, cleanTitle: (String) -> String): SManga = SManga.create().apply {
        url = id
        title = cleanTitle(name)

        author = authorNodes?.mapNotNull { it.data?.name }?.takeIf { it.isNotEmpty() }?.joinToString()
            ?: authors?.joinToString { cleanAuthorSlug(it) }
        artist = artistNodes?.mapNotNull { it.data?.name }?.takeIf { it.isNotEmpty() }?.joinToString()
            ?: artists?.joinToString { cleanAuthorSlug(it) }

        genre = buildSet {
            type?.let { add(it.toTitleCase()) }
            demographics?.forEach { d -> add(d.toTitleCase()) }
            contentRating?.let { add(it.toTitleCase()) }
            genres?.forEach { g -> add(g.toTitleCase()) }
        }.joinToString()

        memo = buildJsonObject {
            urlPath?.let { put("urlPath", it) }
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
        thumbnail_url = urlCover?.let { if (it.startsWith("http")) it else "$baseUrl$it" }
        description = buildString {
            if (isHot == true) append("🔥 HOT ")
            if (isNew == true) append("✨ NEW")
            if (isHot == true || isNew == true) append("\n\n")

            val metadata = buildList {
                originalLanguage?.let { ol ->
                    val label = languages.firstOrNull { it.second == ol }?.first ?: ol
                    add("**Original**: $label")
                }
                translatedLanguage?.let { tl ->
                    val label = languages.firstOrNull { it.second == tl }?.first ?: tl
                    add("**Translated**: $label")
                }
                if (originalPubFrom != null) {
                    val till = originalPubTill?.toString() ?: "Ongoing"
                    add("**Publication**: $originalPubFrom - $till")
                }
                originalPubZone?.takeIf { it.isNotEmpty() }?.let { add("**Region**: $it") }
            }

            if (metadata.isNotEmpty()) {
                append(metadata.joinToString("\n"))
                append("\n\n")
            }

            val stats = buildList {
                scoreVal?.takeIf { it > 0 }?.let { add("**Score**: %.1f".format(it)) }
                follows?.takeIf { it > 0 }?.let { add("**Follows**: $it") }
                reviews?.takeIf { it > 0 }?.let { add("**Reviews**: $it") }
                chapsNormal?.takeIf { it > 0 }?.let { add("**Chapters**: $it") }
            }

            if (stats.isNotEmpty()) {
                append("**Statistics**\n${stats.joinToString(" · ")}")
                append("\n\n")
            }

            if (metadata.isNotEmpty()) {
                append("\n\n---\n\n")
            }

            val summaryText = summary?.text
            if (!summaryText.isNullOrEmpty()) {
                append(summaryText.htmlToMarkdown(baseUrl))
            }

            val links = buildList {
                trackingSites?.mangaUpdates?.let { add("[MangaUpdates](https://www.mangaupdates.com/series.html?id=$it)") }
                trackingSites?.myAnimeList?.let { add("[MyAnimeList](https://myanimelist.net/manga/$it)") }
                trackingSites?.animePlanet?.let { add("[Anime-Planet](https://www.anime-planet.com/manga/$it)") }
                trackingSites?.aniList?.let { add("[AniList](https://anilist.co/manga/$it)") }
                trackingSites?.kitsu?.let { add("[Kitsu](https://kitsu.io/manga/$it)") }
            }

            if (links.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("**External Links**:\n")
                append(links.joinToString("\n") { "- $it" })
            }

            val extras = buildList {
                val pubList = publisherNodes?.mapNotNull { it.data?.name }
                    ?.takeIf { it.isNotEmpty() } ?: publishers
                pubList?.takeIf { it.isNotEmpty() }?.let { add("**Publishers**: ${it.joinToString()}") }

                val tagList = tagNodes?.mapNotNull { it.data?.name }
                    ?.takeIf { it.isNotEmpty() } ?: tags
                tagList?.takeIf { it.isNotEmpty() }?.let { add("**Tags**: ${it.joinToString()}") }
            }

            if (extras.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(extras.joinToString("\n\n"))
            }

            if (!altNames.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("**Alternative Titles**:\n")
                append(altNames.joinToString("\n") { "- $it" })
            }

            val extraInfoText = extraInfo?.text
            if (!extraInfoText.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n**Extra Info**:\n")
                append(extraInfoText.htmlToMarkdown(baseUrl))
            }
        }
        initialized = originalStatus != null
    }
}

private val authorSlugRegex = Regex("^[a-zA-Z0-9]{4,}-(.*)$")

private val urlRegex = Regex("""(?<![\[(])(https?://[^\s<"]+)""")

private val spaceCollapseRegex = Regex("[ \\t]+")
private val trailingSpaceRegex = Regex(" \\n")
private val leadingSpaceRegex = Regex("\\n ")
private val multiNewlineRegex = Regex("\\n{3,}")

// ============================= Helpers ==============================

private fun cleanAuthorSlug(slug: String): String {
    val path = slug.substringAfterLast("/")

    val match = authorSlugRegex.matchEntire(path)

    return if (match != null) {
        val namePart = match.groupValues[1]
        namePart.replace("-", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                }
            }
    } else {
        slug
    }
}

private fun String.toTitleCase(): String = this.replace("_", " ").split(" ").joinToString(" ") { word ->
    word.lowercase().replaceFirstChar {
        if (it.isLowerCase()) it.titlecase() else it.toString()
    }
}

private fun String.htmlToMarkdown(baseUrl: String): String = Jsoup.parseBodyFragment(this, baseUrl).body().let { body ->
    body.select("a").forEach { a ->
        val text = a.text()
        val href = a.absUrl("href")

        if (href.startsWith("http")) {
            a.replaceWith(TextNode("[$text]($href)"))
        } else {
            a.replaceWith(TextNode(text))
        }
    }

    body.select("br").forEach { br -> br.replaceWith(TextNode("\n")) }

    body.select("p, div, li, blockquote, h1, h2, h3, h4, h5, h6").forEach { el ->
        el.after("\n")
    }

    body.wholeText()
        .replace(urlRegex) { match ->
            val url = match.value
            "[$url]($url)"
        }
        .replace(spaceCollapseRegex, " ")
        .replace(trailingSpaceRegex, "\n")
        .replace(leadingSpaceRegex, "\n")
        .replace(multiNewlineRegex, "\n\n")
        .trim()
}

// ============================= Search ===============================

@Serializable
class SearchPagerData(
    @SerialName("get_comic_browse_pager")
    val pager: XComicPaging,
)

@Serializable
class SearchItemsData(
    @SerialName("get_comic_browse_items")
    val items: List<XComicData<ComicNode>>,
)

// ============================= Details ==============================

@Serializable
class ComicNodeData(
    @SerialName("get_comicNode")
    val response: XComicData<ComicNode>,
)

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
class ChapterIndexItem(
    @SerialName("chapter_id") val chapterId: String,
    val chaNum: Float? = null,
    val volNum: Float? = null,
    val title: String? = null,
    val count: Int? = null,
    val datePublic: Long? = null,
)

@Serializable
class ChapterIndexData(
    @SerialName("get_comic_chapterIndex")
    val chapters: List<ChapterIndexItem>,
)

@Serializable
class ChapterDuplicationData(
    @SerialName("get_comic_chapterDuplications")
    val items: List<ApiChapterWrapper>,
)

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
    val imageUrls: List<String>,
)

@Serializable
class ApiChapterWrapper(
    val id: String,
    val data: ChapterData,
)

@Serializable
class ChapterData(
    private val id: String,
    private val comicId: String? = null,
    private val dbStatus: String? = null,
    private val isFinal: Boolean? = null,
    private val volume: JsonElement? = null,
    private val serial: Float? = null,
    @SerialName("dname")
    private val displayName: String,
    private val title: String? = null,
    private val urlPath: String? = null,
    @SerialName("sfw_result")
    private val sfwResult: String? = null,
    @SerialName("chaDuplications")
    private val chaDuplications: String? = null,
    private val dateCreate: Long? = null,
    @SerialName("datePublic")
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
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = id
        name = buildString {
            val number = (chaNum ?: serial)?.toString()?.removeSuffix(".0")
            if (number != null && !displayName.contains(number)) {
                append("Chapter ", number, ": ")
            }
            append(displayName)
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
        } ?: profileNodes?.mapNotNull { it?.data?.name }?.joinToString().takeIf { !it.isNullOrEmpty() }
    }
}

// ========================= Latest Uploads ===========================

@Serializable
class LatestUploadsData(
    @SerialName("get_comic_latestUploads")
    val response: LatestUploadsResult,
)

@Serializable
class LatestUploadsResult(
    val before: Double? = null,
    val items: List<LatestUploadsItem>,
)

@Serializable
class LatestUploadsItem(
    val comic: XComicData<ComicNode>,
    val chapters: List<XComicData<ChapterData>>,
)

@Serializable
class XComicStrings(
    val text: String? = null,
)
