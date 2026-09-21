package eu.kanade.tachiyomi.extension.all.xcomic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ============================= Shared primitives =============================
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
class XComicStrings(
    val text: String? = null,
)

// ====================== Title node (main identity) ========================
@Serializable
class ComicTrackingSites(
    @SerialName("mangaupdates") val mangaUpdates: String? = null,
    @SerialName("myanimelist") val myAnimeList: String? = null,
    @SerialName("animeplanet") val animePlanet: String? = null,
    @SerialName("anilist") val aniList: String? = null,
    val kitsu: String? = null,
)

@Serializable
class TitleTrackingSites(
    val anilist: Int? = null,
    val myanimelist: Int? = null,
    val mangaupdates: String? = null, // hash slug
    val kitsu: Int? = null,
    val animeplanet: String? = null, // text slug
    val shikimori: String? = null, // = MAL id
    val mangabaka: Int? = null,
)

// ============================= Title Browse =============================
@Serializable
class TitleBrowseData(
    @SerialName("get_title_browse_items")
    val items: List<TitleBrowseNode>? = null,
)

@Serializable
class TitleBrowseNode(
    val id: String? = null,
    val data: TitleBrowseItem? = null,
)

// ====================== Comic Probe (browse fan-out) ======================
@Serializable
class ComicProbeEnvelope(
    @SerialName("get_comicNode")
    val response: XComicData<ComicProbeData?>? = null,
)

@Serializable
class ComicProbeData(
    val name: String? = null,
    val subName: String? = null,
    val dbStatus: String? = null,
    val isPublic: Boolean? = null,
    val translatedLanguage: String? = null,
    @SerialName("chaps_normal") val chapsNormal: Int? = null,
    val urlPath: String? = null,
    @SerialName("urlCover") val urlCover: String? = null,
) {
    fun isLive(): Boolean = isPublic != false && (dbStatus == null || dbStatus == "normal")
}

@Serializable
class TitleBrowseItem(
    val title: String? = null,
    @SerialName("native_title") val nativeTitle: String? = null,
    @SerialName("romanized_title") val romanizedTitle: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("translated_languages") val translatedLanguages: List<String?>? = null, // null elements 3×
    val type: String? = null,
    @SerialName("chap_last_public_at") val chapLastPublicAt: Long? = null,
    @SerialName("cover_local_url") val coverLocalUrl: String? = null, // full-res — prefer
    @SerialName("cover_url") val coverUrl: String? = null, // x250 thumb — fallback
    @SerialName("comic_ids") val comicIds: List<String>? = null,
)

// ====================== Title Node (details backbone) =====================
@Serializable
class TitleNodeEnvelope(
    @SerialName("get_title_titleNode")
    val response: XComicData<TitleNodeData?>? = null,
)

@Serializable
class TitleNodeData(
    val id: String? = null,
    val title: String? = null,
    @SerialName("alt_titles") val altTitles: List<String?>? = null,
    @SerialName("native_title") val nativeTitle: String? = null,
    @SerialName("romanized_title") val romanizedTitle: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("translated_languages") val translatedLanguages: List<String?>? = null,
    val authors: List<String>? = null,
    val artists: List<String>? = null,
    // taxonomy — SLUG STRINGS (doc's Int claim wrong; triple-verified live)
    @SerialName("content_rating_id") val contentRating: String? = null,
    @SerialName("type_id") val typeId: String? = null,
    @SerialName("demographic_ids") val demographicIds: List<String>? = null,
    @SerialName("genre_ids") val genreIds: List<String>? = null,
    @SerialName("format_ids") val formatIds: List<String>? = null,
    val year: Int? = null,
    val type: String? = null,
    val status: String? = null,
    val description: String? = null,
    @SerialName("cover_local_url") val coverLocalUrl: String? = null, // full-res — prefer
    @SerialName("cover_local") val coverLocal: String? = null, // raw key (redundant)
    @SerialName("cover_url") val coverUrl: String? = null, // x250 thumb — fallback
    val urlPath: String? = null,
    @SerialName("total_comics") val totalComics: Int? = null,
    @SerialName("total_chapters") val totalChapters: Int? = null,
    @SerialName("total_follows") val totalFollows: Int? = null,
    @SerialName("total_reviews") val totalReviews: Int? = null,
    @SerialName("total_comments") val totalComments: Int? = null,
    @SerialName("vote_avg") val voteAvg: Float? = null,
    @SerialName("vote_users") val voteUsers: Int? = null,
    @SerialName("vote_bay") val voteBay: Float? = null,
    @SerialName("vote_val") val voteVal: Float? = null,
    @SerialName("chap_last_public_at") val chapLastPublicAt: Long? = null, // work-level freshness
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
    @SerialName("is_merged") val isMerged: Boolean? = null,
    @SerialName("merged_to") val mergedTo: String? = null,
    @SerialName("comic_ids") val comicIds: List<String>? = null,
    @SerialName("tracking_sites") val trackingSites: TitleTrackingSites? = null,
)

fun TitleTrackingSites?.toMarkdownLinks(): List<String> = buildList {
    this@toMarkdownLinks?.anilist?.let { add("[AniList](https://anilist.co/manga/$it)") }
    this@toMarkdownLinks?.myanimelist?.let { add("[MyAnimeList](https://myanimelist.net/manga/$it)") }
    this@toMarkdownLinks?.mangaupdates?.let { add("[MangaUpdates](https://www.mangaupdates.com/series/$it)") }
    this@toMarkdownLinks?.kitsu?.let { add("[Kitsu](https://kitsu.app/manga/$it)") }
    this@toMarkdownLinks?.animeplanet?.let { add("[Anime-Planet](https://www.anime-planet.com/manga/$it)") }
    this@toMarkdownLinks?.shikimori?.let { add("[Shikimori](https://shikimori.io/mangas/$it)") }
    this@toMarkdownLinks?.mangabaka?.let { add("[MangaBaka](https://mangabaka.org/$it)") }
}

// ================== Comic node (source / upload identity) =================
@Serializable
class ComicNode(
    private val id: String,
    private val name: String,
    val subName: String? = null,
    private val altNames: List<String>? = null,
    private val authors: List<String>? = null,
    private val authorNodes: List<XComicData<XComicName?>>? = null,
    private val artists: List<String>? = null,
    private val artistNodes: List<XComicData<XComicName?>>? = null,
    private val originalLanguage: String? = null,
    val translatedLanguage: String? = null,
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
    private val readDirection: String? = null,
    private val dbStatus: String? = null,
    private val isPublic: Boolean? = null,
    @SerialName("is_hot") private val isHot: Boolean? = null,
    @SerialName("is_new") private val isNew: Boolean? = null,
    private val follows: Int? = null,
    private val reviews: Int? = null,
    @SerialName("comments_total") private val commentsTotal: Int? = null,
    @SerialName("score_val") private val scoreVal: Float? = null,
    @SerialName("chaps_normal") val chapsNormal: Int? = null,
    @SerialName("dateUpload") val dateUpload: Long? = null,
    @SerialName("chapterNode_up_to") val chapterUpTo: ChapterUpToNode? = null,
    private val trackingSites: ComicTrackingSites? = null,
    private val urlPath: String? = null,
    private val urlCover: String? = null,
) {
    fun isLive(): Boolean = isPublic != false && (dbStatus == null || dbStatus == "normal")

    fun toSManga(baseUrl: String, cleanTitle: (String) -> String): SManga = SManga.create().apply {
        url = id
        title = cleanTitle(name)

        author = authorNodes?.mapNotNull { it.data?.name }?.takeIf { it.isNotEmpty() }?.joinToString()
        artist = artistNodes?.mapNotNull { it.data?.name }?.takeIf { it.isNotEmpty() }?.joinToString()

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

                readDirection?.let { dir ->
                    val directionValues = listOf(
                        "ttb" to "⬇️ Top To Bottom",
                        "rtl" to "⬅️ Right To Left",
                        "ltr" to "➡️ Left To Right",
                    )
                    val label = directionValues.firstOrNull { it.first == dir }?.second ?: dir
                    add("**Read Direction**: $label")
                }
            }

            if (metadata.isNotEmpty()) {
                append(metadata.joinToString("\n"))
                append("\n\n")
            }

            val stats = buildList {
                scoreVal?.takeIf { it > 0 }?.let { add("**Score**: %.1f".format(it)) }
                follows?.takeIf { it > 0 }?.let { add("**Follows**: $it") }
                reviews?.takeIf { it > 0 }?.let { add("**Reviews**: $it") }
                commentsTotal?.takeIf { it > 0 }?.let { add("**Comments**: $it") }
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
                append(summaryText.toMarkdownUrls())
            }

            val links = buildList {
                trackingSites?.mangaUpdates?.let { add("[MangaUpdates](https://www.mangaupdates.com/series/$it)") }
                trackingSites?.myAnimeList?.let { add("[MyAnimeList](https://myanimelist.net/manga/$it)") }
                trackingSites?.animePlanet?.let { add("[Anime-Planet](https://www.anime-planet.com/manga/$it)") }
                trackingSites?.aniList?.let { add("[AniList](https://anilist.co/manga/$it)") }
                trackingSites?.kitsu?.let { add("[Kitsu](https://kitsu.app/manga/$it)") }
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
                append(extraInfoText.toMarkdownUrls())
            }
        }
        initialized = originalStatus != null
    }
}

// ================== Comic browse (legacy path / deep links) ===============
@Serializable
class ComicNodeData(
    @SerialName("get_comicNode")
    val response: XComicData<ComicNode>,
)

@Serializable
class ChapterUpToNode(
    val id: String? = null,
    val data: ChapterUpToData? = null,
)

@Serializable
class ChapterUpToData(
    val dname: String? = null,
    @SerialName("datePublic") val datePublic: Long? = null,
)

// ================================ Chapters ================================
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
    private val displayName: String = "",
    private val title: String? = null,
    private val urlPath: String? = null,
    @SerialName("sfw_result")
    private val sfwResult: JsonElement? = null,
    @SerialName("chaDuplications")
    private val chaDuplications: JsonElement? = null,
    private val dateCreate: Long? = null,
    @SerialName("datePublic")
    private val datePublic: Long? = null,
    private val dateModify: Long? = null,
    private val chaNum: Float? = null,
    private val volNum: Float? = null,
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
        } ?: profileNodes?.mapNotNull { it?.data?.name }?.joinToString().takeIf { !it.isNullOrEmpty() }
    }
}

// ================================ Helpers =================================

private fun String.toTitleCase(): String = this.replace("_", " ").split(" ").joinToString(" ") { word ->
    word.lowercase().replaceFirstChar {
        if (it.isLowerCase()) it.titlecase() else it.toString()
    }
}

private val urlRegex = Regex("""(?<![\[(])(https?://[^\s<"]+)""")

fun String.toMarkdownUrls(): String = this.replace(urlRegex) { match ->
    val url = match.value
    "[$url]($url)"
}
