package eu.kanade.tachiyomi.extension.ar.mangaorigin

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class FeedResponse(private val feed: Feed) {
    val entries get() = feed.entry.orEmpty()
    val total get() = feed.totalResults!!.t.toInt()
}

@Serializable
class Feed(
    // Missing when the feed is asked for one post
    @SerialName("openSearch\$totalResults") val totalResults: TextDto? = null,
    val entry: List<EntryDto>? = null,
)

@Serializable
class EntryDto(
    private val title: TextDto,
    private val published: TextDto,
    private val link: List<LinkDto>,
    private val category: List<CategoryDto>? = null,
    @SerialName("content") private val body: TextDto? = null,
    @SerialName("media\$thumbnail") private val thumbnail: ThumbnailDto? = null,
) {
    val path get() = link.first { it.rel == "alternate" }.href.toHttpUrl().encodedPath

    val html get() = body?.t

    val isSeries get() = title.t.trim().endsWith(SERIES_SUFFIX)

    // Genres are Arabic words, while each series has one label with its romanized name
    val seriesLabel get() = labels.firstOrNull { !ARABIC_REGEX.containsMatchIn(it) }

    private val labels get() = category.orEmpty().map { it.term }

    fun toSManga() = SManga.create().apply {
        url = path
        title = this@EntryDto.title.t.trim().removeSuffix(SERIES_SUFFIX).trim()
        thumbnail_url = thumbnail?.url?.toOriginalSize()
        genre = labels.filter { it != seriesLabel }.joinToString().ifEmpty { null }
    }

    // "<full title> <Arabic title>. التصنيف: <genres>. القصة: <story>", then a link to the chapters
    fun toDetailedSManga() = toSManga().apply {
        val document = Jsoup.parseBodyFragment(html!!)
        val text = document.text()
        val names = text.substringBefore(GENRES_HEADING, "").trim()
        val arabicStart = ARABIC_REGEX.find(names)?.range?.first ?: names.length
        val chaptersLink = document.selectFirst("a[href*=/search/label/]")?.text()
        val story = text.substringAfter(STORY_HEADING, "").let { story -> chaptersLink?.let(story::substringBefore) ?: story }
        description = MarkdownDescription.Builder()
            .description(story.trim().ifEmpty { null })
            .altTitles(
                listOf(names.take(arabicStart).trim(), names.drop(arabicStart).trim().trimEnd('.'))
                    .filter { it.isNotEmpty() && !it.equals(title, ignoreCase = true) },
            )
            .build()
        thumbnail_url = document.selectFirst("img")?.absUrl("src")?.toOriginalSize() ?: thumbnail_url
    }

    fun toSChapter() = SChapter.create().apply {
        url = path
        name = title.t.trim()
        date_upload = Instant.tryParse(published.t)
    }
}

@Serializable
class TextDto(@SerialName("\$t") val t: String)

@Serializable
class LinkDto(val rel: String, val href: String)

@Serializable
class CategoryDto(val term: String)

@Serializable
class ThumbnailDto(val url: String)

// Blogger serves an image at the size written in its address, and s0 is the original
fun String.toOriginalSize() = replace(SIZE_SEGMENT_REGEX, "/s0/").replace(SIZE_SUFFIX_REGEX, "=s0")

private const val SERIES_SUFFIX = "- القصة"
private const val GENRES_HEADING = "التصنيف:"
private const val STORY_HEADING = "القصة:"
private val ARABIC_REGEX = Regex("""[؀-ۿ]""")
private val SIZE_SEGMENT_REGEX = Regex("""/[swh]\d+[^/]*/(?=[^/]+$)""")
private val SIZE_SUFFIX_REGEX = Regex("""=[swh]\d+[^/=]*$""")
