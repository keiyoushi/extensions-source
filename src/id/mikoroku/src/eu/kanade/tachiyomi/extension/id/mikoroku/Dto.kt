package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Locale
import kotlin.time.Instant

@Serializable
class BloggerFeedResponse(
    val feed: BloggerFeed,
)

@Serializable
class BloggerEntryResponse(
    val entry: BloggerEntry,
)

@Serializable
class BloggerFeed(
    val entry: List<BloggerEntry> = emptyList(),
)

@Serializable
class BloggerEntry(
    private val id: BloggerText,
    private val published: BloggerText? = null,
    private val updated: BloggerText? = null,
    private val category: List<BloggerCategory> = emptyList(),
    private val title: BloggerText,
    private val content: BloggerText? = null,
    private val link: List<BloggerLink> = emptyList(),
    @SerialName("media\$thumbnail") private val thumbnail: BloggerThumbnail? = null,
) {
    val slug: String
        get() = title.value.toMikoRokuSlug()

    fun hasCategory(value: String): Boolean = category.any { it.term.equals(value, ignoreCase = true) }

    fun isChapterFor(mangaTitle: String): Boolean {
        val match = CHAPTER_TITLE_REGEX.matchEntire(title.value.trim()) ?: return false
        return hasCategory("Chapter") &&
            match.groupValues[1].toMikoRokuSlug() == mangaTitle.toMikoRokuSlug()
    }

    fun toSManga(): SManga {
        val mangaTitle = title.value
        val mangaSlug = slug
        val coverUrl = coverUrl()
        require(mangaTitle.isNotEmpty()) { "Manga title is missing" }
        require(mangaSlug.isNotEmpty()) { "Manga slug is missing for $mangaTitle" }

        return SManga.create().apply {
            url = mangaSlug
            title = mangaTitle
            thumbnail_url = coverUrl
        }
    }

    fun toSMangaDetails(mangaSlug: String): SManga {
        val document = contentDocument()
        val info = document.extraInfo()
        val synopsis = document.selectFirst("#synopsis")?.text()?.takeIf(String::isNotEmpty)
        val altTitle = info["alt"]?.takeIf(String::isNotEmpty)

        return toSManga().apply {
            url = mangaSlug
            thumbnail_url = document.selectFirst(".separator a[href]")
                ?.attr("href")
                ?.toBloggerImageSize("s500")
                ?: thumbnail_url
            author = info["author"]
            artist = info["artist"]
            genre = info["genre"]
            status = category.toStatus()
            description = buildList {
                altTitle?.let { add("Judul alternatif: $it") }
                synopsis?.let(::add)
            }.joinToString("\n\n").takeIf(String::isNotEmpty)
        }
    }

    fun toSChapter(): SChapter? {
        val chapterNumber = CHAPTER_TITLE_REGEX.matchEntire(title.value.trim())?.groupValues?.get(2)
            ?: return null
        val alternateUrl = link.firstOrNull { it.rel == "alternate" }?.href?.toHttpUrlOrNull()
            ?: return null
        val postId = id.value.substringAfterLast(".post-").takeIf(String::isNotEmpty)
            ?: return null

        return SChapter.create().apply {
            url = "${alternateUrl.encodedPath}#$postId"
            name = "Chapter $chapterNumber"
            chapter_number = chapterNumber.toFloatOrNull() ?: -1F
            date_upload = Instant.parseOrNull(updated?.value ?: published?.value.orEmpty())
                ?.toEpochMilliseconds()
                ?: 0L
        }
    }

    fun pageUrls(): List<String> = contentDocument()
        .select("img")
        .mapNotNull { image ->
            sequenceOf("data-src", "data-original", "src")
                .map(image::attr)
                .firstOrNull(String::isNotEmpty)
                ?.toHttpUrlOrNull()
        }
        .filterNot { url ->
            val fileName = url.pathSegments.lastOrNull()?.lowercase(Locale.ROOT).orEmpty()
            NON_PAGE_IMAGE_NAMES.any(fileName::contains)
        }
        .map { it.toString().toBloggerImageSize("s0") }
        .distinct()

    private fun coverUrl(): String? {
        val thumbnailUrl = thumbnail?.url
        if (!thumbnailUrl.isNullOrEmpty()) return thumbnailUrl.toBloggerImageSize("s500")

        return contentDocument().selectFirst("img")
            ?.attr("src")
            ?.takeIf(String::isNotEmpty)
            ?.toBloggerImageSize("s500")
    }

    private fun contentDocument(): Document = Jsoup.parseBodyFragment(content?.value.orEmpty(), "https://mikoroku.com/")
}

@Serializable
class BloggerText(
    @SerialName("\$t") val value: String,
)

@Serializable
class BloggerCategory(
    val term: String,
)

@Serializable
class BloggerLink(
    val rel: String,
    val href: String,
)

@Serializable
class BloggerThumbnail(
    val url: String,
)

private fun Document.extraInfo(): Map<String, String> = select("#extra-info .info-item")
    .mapNotNull { item ->
        val key = item.ownText()
            .substringBefore(':')
            .trim()
            .lowercase(Locale.ROOT)
            .takeIf(String::isNotEmpty)
            ?: return@mapNotNull null
        val value = item.selectFirst(".info-value")?.text()?.takeIf(String::isNotEmpty)
            ?: return@mapNotNull null
        key to value
    }
    .toMap()

private fun List<BloggerCategory>.toStatus(): Int {
    val labels = map { it.term.lowercase(Locale.ROOT) }.toSet()
    return when {
        "ongoing" in labels -> SManga.ONGOING
        "completed" in labels -> SManga.COMPLETED
        "hiatus" in labels -> SManga.ON_HIATUS
        "dropped" in labels || "canceled" in labels -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

private fun String.toMikoRokuSlug(): String = lowercase(Locale.ROOT)
    .replace(SLUG_INVALID_CHARS_REGEX, "")
    .replace(SLUG_WHITESPACE_REGEX, "-")
    .replace(SLUG_DASHES_REGEX, "-")
    .trim('-')

private fun String.toBloggerImageSize(size: String): String = replace(BLOGGER_PATH_SIZE_REGEX, "/$size/")
    .replace(BLOGGER_QUERY_SIZE_REGEX, "=$size")

private val CHAPTER_TITLE_REGEX = Regex("""(?i)^(.+?)\s+chapter\s*(\d+(?:\.\d+)?)$""")
private val SLUG_INVALID_CHARS_REGEX = Regex("""[^a-z0-9\s-]""")
private val SLUG_WHITESPACE_REGEX = Regex("""\s+""")
private val SLUG_DASHES_REGEX = Regex("""-+""")
private val BLOGGER_PATH_SIZE_REGEX = Regex("""/s\d+(?:-[a-z0-9-]+)?/""", RegexOption.IGNORE_CASE)
private val BLOGGER_QUERY_SIZE_REGEX = Regex("""=s\d+(?:-[a-z0-9-]+)?$""", RegexOption.IGNORE_CASE)
private val NON_PAGE_IMAGE_NAMES = listOf("credit", "logo", "banner", "avatar", "placeholder", "thumbnail")
