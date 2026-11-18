package eu.kanade.tachiyomi.extension.id.siimanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
class ComicListDto(
    val items: List<ComicDto>,
    val nextCursor: String? = null,
)

@Serializable
class ComicDto(
    private val slug: String,
    private val title: String,
    private val cover: CoverDto? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val genres: List<String> = emptyList(),
    private val synopsis: String? = null,
    private val alternativeTitles: List<String> = emptyList(),
    private val ratingAvg: Double? = null,
    private val ratingCount: Int = 0,
) {
    val coverUrl get() = cover?.url

    fun toSManga() = SManga.create().apply {
        url = mangaPath(slug)
        applyListFields()
    }

    // Older entries keep a post id in url and the path in memo, so details update the entry in place
    fun applyTo(manga: SManga, baseUrl: String) = manga.apply {
        applyListFields()
        description = MarkdownDescription.Builder()
            .rating(ratingAvg?.takeIf { ratingCount > 0 }, max = 5, votes = ratingCount)
            .description(synopsis?.let { Jsoup.parseBodyFragment(it, baseUrl).body() })
            .altTitles(alternativeTitles)
            .build()
    }

    private fun SManga.applyListFields() {
        title = this@ComicDto.title
        thumbnail_url = coverUrl?.toHttpUrl()?.newBuilder()?.fragment(slug)?.build()?.toString()
        genre = (listOfNotNull(type?.lowercase()?.replaceFirstChar(Char::titlecase)) + genres).distinct().takeIf { it.isNotEmpty() }?.joinToString()
        status = when (this@ComicDto.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class CoverDto(
    val url: String,
)

@Serializable
class ChapterListDto(
    val items: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    private val slug: String,
    private val name: String,
    private val title: String? = null,
    private val number: Float,
    private val publishedAt: String? = null,
    private val locked: Boolean,
    private val price: Int = 0,
    private val access: String? = null,
    private val freeAt: String? = null,
) {
    // The site reader's rule: bought, membership, free or past freeAt chapters open
    val isLocked get() = access !in OPEN_ACCESS &&
        (freeAt == null || Instant.tryParse(freeAt) > Clock.System.now().toEpochMilliseconds()) &&
        (locked || price > 0)

    fun toSChapter(mangaPath: String) = SChapter.create().apply {
        url = slug
        name = buildString {
            if (isLocked) append("🔒 ")
            append(this@ChapterDto.name)
            title?.takeIf(String::isNotBlank)?.let { append(" - ", it) }
        }
        chapter_number = number
        date_upload = Instant.tryParse(publishedAt)
        memo = buildJsonObject { put(MEMO_MANGA_PATH, mangaPath) }
    }
}

private val OPEN_ACCESS = setOf("FREE", "OWNED", "MEMBERSHIP")

@Serializable
class PagesDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val url: String,
)

@Serializable
class GenreDto(
    val slug: String,
    val name: String,
)

@Serializable
class TermsDto(
    val items: List<GenreDto>,
)

@Serializable
class ErrorDto(
    val code: String? = null,
)

// Madara's url and memo shapes, so library entries from before the site's move keep working
fun mangaPath(slug: String) = "/komik/$slug/"

const val MEMO_PATH = "path"

const val MEMO_MANGA_PATH = "mangaPath"
