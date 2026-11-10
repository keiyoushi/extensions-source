package eu.kanade.tachiyomi.extension.en.tailspace

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class BrowseData(
    // Ads are mixed in with the comics
    val comicsAndAds: List<JsonObject>,
    val numberOfPages: Int,
)

@Serializable
class BrowseComic(
    private val id: Long,
    private val name: String,
    private val slug: String,
    private val artistName: String? = null,
    private val thumbnailVersion: Int? = null,
    private val state: String? = null,
    private val category: String? = null,
    private val isSfw: Boolean? = null,
    private val tags: List<Tag>? = null,
) {
    fun toSManga(coalesce: Boolean) = SManga.create().apply {
        url = slug
        title = if (coalesce) name.seriesTitle() else name
        thumbnail_url = thumbnailUrl(id, thumbnailVersion)
        author = artistName
        artist = artistName
        genre = genres(listOf(category), listOf(isSfw), tags.orEmpty())
        status = parseStatus(listOf(state))
    }
}

@Serializable
class ComicPageData(
    val comic: Comic? = null,
)

@Serializable
class Comic(
    val id: Long,
    val name: String,
    val slug: String,
    val description: String? = null,
    val category: String? = null,
    val isSfw: Boolean? = null,
    val state: String? = null,
    val thumbnailVersion: Int? = null,
    private val artist: Artist? = null,
    private val additionalArtists: List<Artist>? = null,
    val tags: List<Tag>? = null,
    val pages: List<ComicPage>? = null,
    val published: Long? = null,
    val previousComic: LinkedComic? = null,
    val nextComic: LinkedComic? = null,
) {
    val artists: String?
        get() = (listOfNotNull(artist) + additionalArtists.orEmpty()).map { it.name }
            .distinct().joinToString().ifEmpty { null }
}

@Serializable
class ComicPage(
    val token: String,
    val pageNumber: Int,
    val fileType: String? = null,
)

@Serializable
class LinkedComic(
    val slug: String,
)

@Serializable
class Artist(
    val name: String,
)

@Serializable
class Tag(
    val id: Int,
    val name: String,
    val category: String? = null,
)

@Serializable
class TagsResponse(
    val data: List<Tag>,
)

// TipTap rich text document
@Serializable
class DocNode(
    private val type: String,
    private val text: String? = null,
    private val content: List<DocNode> = emptyList(),
) {
    fun toText(): String = when (type) {
        "text" -> text.orEmpty()
        "hardBreak" -> "\n"
        "paragraph", "heading" -> content.joinToString("") { it.toText() } + "\n"
        else -> content.joinToString("") { it.toText() }
    }
}

fun parseDescription(description: String): String = if (description.startsWith("{")) {
    description.parseAs<DocNode>().toText().trim()
} else {
    description
}

// Drops part markers like " - Chapter 2", " (Ch 3)" or " Vol 4: Subtitle"
private val PART_SUFFIX = Regex(
    """\s*(?:[-–—:,|]\s*|\()?(?:\b(?:chapter|chap|ch|part|pt|vol|volume|issue|episode|ep|act)\b\.?\s*)+#?\d+\b.*$""",
    RegexOption.IGNORE_CASE,
)

fun String.seriesTitle(): String = replace(PART_SUFFIX, "").trim().ifEmpty { this }

fun thumbnailUrl(id: Long, version: Int?) = "$PAGES_PATH/comics/$id/thumbnail-2x.webp?v=${version ?: 0}"

fun genres(categories: List<String?>, sfw: List<Boolean?>, tags: List<Tag>): String? = buildSet {
    categories.filterNotNullTo(this)
    sfw.filterNotNull().mapTo(this) { if (it) "Safe-ish" else "Explicit" }
    tags.mapTo(this) { it.name }
}.joinToString().ifEmpty { null }

fun parseStatus(states: List<String?>): Int = when {
    states.any { it == "wip" } -> SManga.ONGOING
    states.isNotEmpty() && states.all { it == "finished" } -> SManga.COMPLETED
    else -> SManga.UNKNOWN
}

const val PAGES_PATH = "https://pics.tailspace.com"
