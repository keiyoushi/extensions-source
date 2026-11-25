package eu.kanade.tachiyomi.extension.all.tvymanga

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.parser.Parser
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlin.time.Instant

@Serializable
class CategoryDto(
    val id: Int,
    val name: String,
    val slug: String,
    val count: Int,
)

@Serializable
class RenderedDto(val rendered: String)

@Serializable
class PostDto(
    val id: Int,
    @SerialName("date_gmt") val dateGmt: String,
    val slug: String,
    private val title: RenderedDto,
    val categories: List<Int>,
) {
    val name: String get() = title.rendered.decodeHtml()

    val seriesKey: String get() = seriesName(name).toKey()

    fun toSChapter() = SChapter.create().apply {
        url = "/$slug/"
        name = this@PostDto.name
        chapter_number = chapterNumber(this@PostDto.name)
        // The WordPress API sends GMT dates without an offset
        date_upload = Instant.tryParse("${dateGmt}Z")
    }
}

@Serializable
class PostContentDto(val content: RenderedDto)

/** A manga of the site: a WordPress category, or the chapters of one name that have no category. */
class Series(
    val key: String,
    val title: String,
    val url: String,
    var count: Int,
    val categoryId: Int? = null,
    val newestChapterSlug: String? = null,
)

/** Maps the site's categories to series. */
class SeriesIndex(categories: List<CategoryDto>) {
    /** "Uncategorized": chapters without a series category. */
    val looseIds = categories.filter { it.slug in LOOSE_SLUGS }.map { it.id }

    val englishId = categories.firstOrNull { it.slug == ENGLISH_SLUG }?.id

    val real = categories.filter { it.id !in looseIds && it.id != englishId && it.count > 0 }

    private val realById = real.associateBy { it.id }
    private val realByKey = real.associateBy { it.name.decodeHtml().toKey() }

    /** Keys of every category series, to spot a chapter filed under the wrong one. */
    val categoryKeys = realByKey.keys

    fun categorySeries(category: CategoryDto) = Series(
        key = category.name.decodeHtml().toKey(),
        title = category.name.decodeHtml(),
        url = "/category/${category.slug}/",
        count = category.count,
        categoryId = category.id,
    )

    fun seriesOf(post: PostDto, lang: String): Series {
        post.categories.firstNotNullOfOrNull { realById[it] }?.let { return categorySeries(it) }
        val name = seriesName(post.name)
        val key = name.toKey()
        realByKey[key]?.takeIf { lang == LANG_ES }?.let { return categorySeries(it) }
        return Series(key, name, "/?s=${name.urlEncode()}&$LANG_PARAM=$lang", 1, newestChapterSlug = post.slug)
    }

    companion object {
        private val LOOSE_SLUGS = setOf("uncategorized", "sin-categoria")
        private const val ENGLISH_SLUG = "ingles"
    }
}

const val LANG_ES = "es"
const val LANG_EN = "en"
const val LANG_PARAM = "tvy"

private val CHAPTER_WORD_REGEX = Regex("""(?i)^(.*?)[\s:,\-–]*\b(?:cap[ií]tulo|chapter|cap\.)\s*(\d+(?:[.,]\d+)?)""")
private val LAST_NUMBER_REGEX = Regex("""^(.*?)\s*(?<![\d.,])(\d+(?:[.,]\d+)?)\D*$""")

/** "Kanojo, Okarishimasu 447" -> "Kanojo, Okarishimasu"; "Jujutsu kaisen chapter 217【Read Manga Online】" -> "Jujutsu kaisen". */
fun seriesName(title: String): String {
    val match = CHAPTER_WORD_REGEX.find(title) ?: LAST_NUMBER_REGEX.find(title)
    val name = match?.groupValues?.get(1)?.trim()?.trimEnd('-', '–', ':', ',', ' ')
    return name?.takeIf { it.isNotBlank() } ?: title
}

fun chapterNumber(title: String): Float {
    val match = CHAPTER_WORD_REGEX.find(title) ?: LAST_NUMBER_REGEX.find(title)
    return match?.groupValues?.get(2)?.replace(',', '.')?.toFloatOrNull() ?: -1f
}

/** Case and punctuation insensitive, so "KANOJO OKARISHIMASU" matches "Kanojo, Okarishimasu". */
fun String.toKey() = lowercase(Locale.ROOT)
    .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
    .replace(NON_ALPHANUMERIC, "")

private val NON_ALPHANUMERIC = Regex("""[^a-z0-9]""")

fun String.decodeHtml() = Parser.unescapeEntities(this, false).trim()

fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
