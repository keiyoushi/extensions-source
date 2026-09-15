package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.Normalizer
import kotlin.time.Instant

@Serializable
class MangaDto(
    val title: String,
    val slug: String,
    val altTitle: String = "",
    val img: String = "",
    val cover: String = "",
    val desc: String = "",
    val genres: List<String> = emptyList(),
    val author: String = "",
    val artist: String = "",
    val status: String = "",
    val type: String = "",
    val rating: Double = 0.0,
    val updatedAt: Long = 0,
    val isDraft: Boolean = false,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        val mangaUrl = "$baseUrl/detail".toHttpUrl().newBuilder().addQueryParameter("slug", slug).build()
        url = mangaUrl.encodedPath + "?" + mangaUrl.encodedQuery
        title = this@MangaDto.title
        thumbnail_url = (cover.ifBlank { img }).takeIf { it.isNotBlank() && it != "-" }?.let {
            if (it.startsWith("http")) it else MikoRoku.RAW_URL + it.removePrefix("/")
        }
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        genre = genres.joinToString()
        description = buildString {
            append(Jsoup.parseBodyFragment(desc, baseUrl).text())
            if (altTitle.isNotBlank()) append("\n\nAlternative titles: ").append(altTitle)
        }
        status = when (this@MangaDto.status.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "dropped", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class FirestoreDocument<T>(val fields: T, val name: String = "")

@Serializable
class FirestoreList<T>(val documents: List<FirestoreDocument<T>> = emptyList(), val nextPageToken: String? = null)

@Serializable
class StringValue(val stringValue: String = "")

@Serializable
class BooleanValue(val booleanValue: Boolean)

@Serializable
class NumberValue(private val integerValue: String? = null, private val doubleValue: Double? = null, private val timestampValue: String? = null) {
    val number: Double get() = doubleValue ?: integerValue?.toDoubleOrNull() ?: 0.0
    val time: Long get() = timestampValue?.let { Instant.tryParse(it) } ?: number.toLong()
}

@Serializable
class MapValue<T>(val mapValue: FirestoreDocument<T>)

@Serializable
class ArrayValue<T>(private val arrayValue: ArrayContents<T>) {
    val values: List<T> get() = arrayValue.values
}

@Serializable
class ArrayContents<T>(val values: List<T> = emptyList())

@Serializable
class SummaryFields(val list: ArrayValue<MapValue<MangaFields>>)

@Serializable
class MangaFields(
    private val title: StringValue,
    val slug: StringValue? = null,
    val githubSlug: StringValue? = null,
    private val altTitle: StringValue? = null,
    private val img: StringValue? = null,
    private val cover: StringValue? = null,
    private val desc: StringValue? = null,
    private val genres: ArrayValue<StringValue>? = null,
    private val author: StringValue? = null,
    private val artist: StringValue? = null,
    private val status: StringValue? = null,
    private val type: StringValue? = null,
    private val rating: NumberValue? = null,
    private val updatedAt: NumberValue? = null,
    @SerialName("isDraft") private val draft: BooleanValue? = null,
) {
    val isDraft get() = draft?.booleanValue == true

    fun toManga(slug: String, old: MangaDto?) = MangaDto(
        title = title.stringValue,
        slug = slug,
        altTitle = altTitle.textOr(old?.altTitle),
        img = img.textOr(old?.img),
        cover = cover.textOr(old?.cover),
        desc = desc.textOr(old?.desc),
        genres = genres?.values?.map { it.stringValue } ?: old?.genres.orEmpty(),
        author = author.textOr(old?.author),
        artist = artist.textOr(old?.artist),
        status = status.textOr(old?.status),
        type = type.textOr(old?.type),
        rating = rating?.number ?: old?.rating ?: 0.0,
        updatedAt = updatedAt?.time ?: old?.updatedAt ?: 0,
        isDraft = isDraft,
    )
}

fun mergeCatalog(github: List<MangaDto>, summary: List<MapValue<MangaFields>>): List<MangaDto> {
    val catalog = github.associateByTo(linkedMapOf()) { it.slug }
    summary.forEach { entry ->
        val fields = entry.mapValue.fields
        val slug = requireNotNull(fields.slug).stringValue
        val current = catalog.remove(slug)
        val linked = fields.githubSlug?.stringValue?.let(catalog::remove)
        if (!fields.isDraft) catalog[slug] = fields.toManga(slug, current ?: linked)
    }
    return catalog.values.filterNot { it.isDraft || it.type.contains("novel", ignoreCase = true) }
}

private fun StringValue?.textOr(old: String?) = this?.stringValue?.takeIf { it.isNotBlank() } ?: old.orEmpty()

@Serializable
class ChapterFields(
    val title: StringValue,
    private val num: StringValue? = null,
    private val date: NumberValue? = null,
    private val createdAt: NumberValue? = null,
    private val images: ArrayValue<StringValue>? = null,
    private val content: StringValue? = null,
    @SerialName("isDraft") private val draft: BooleanValue? = null,
) {
    val isDraft get() = draft?.booleanValue == true
    val dateUpload get() = date?.time ?: createdAt?.time ?: 0L

    fun chapterNumber(id: String) = num?.stringValue?.toFloatOrNull()
        ?: id.toFloatOrNull()
        ?: title.stringValue.chapterNumber()

    fun pageUrls(baseUrl: String): List<String> = images?.values?.map { it.stringValue.trim() }
        ?.filter { it.startsWith("https://") || it.startsWith("http://") }
        ?.takeIf { it.isNotEmpty() }
        ?: Jsoup.parseBodyFragment(content?.stringValue.orEmpty(), baseUrl).select("img").mapNotNull { it.imageUrl() }
}

fun FirestoreDocument<ChapterFields>.toSChapter(slug: String, baseUrl: String): SChapter = SChapter.create().apply {
    val id = this@toSChapter.name.substringAfterLast('/')
    val readerUrl = "$baseUrl/manga-reader".toHttpUrl().newBuilder()
        .addQueryParameter("slug", slug)
        .addQueryParameter("chapter", id)
        .build()
    url = readerUrl.encodedPath + "?" + readerUrl.encodedQuery
    name = fields.title.stringValue.ifBlank { "Chapter $id" }
    chapter_number = fields.chapterNumber(id)
    date_upload = fields.dateUpload
}

@Serializable
class BloggerResponse(val feed: BloggerFeed)

@Serializable
class BloggerFeed(val entry: List<BloggerEntry> = emptyList())

@Serializable
class BloggerText(@SerialName("\$t") val text: String)

@Serializable
class BloggerCategory(val term: String)

@Serializable
class BloggerLink(val rel: String, val href: String)

@Serializable
class BloggerEntry(
    private val title: BloggerText,
    private val published: BloggerText,
    private val category: List<BloggerCategory>,
    private val link: List<BloggerLink>,
) {
    fun isChapterOf(mangaTitle: String) = category.any { it.term == "Chapter" } &&
        category.any { it.term.normalizeTitle() == mangaTitle.normalizeTitle() }

    fun toSChapter(mirror: Boolean) = SChapter.create().apply {
        val postUrl = link.first { it.rel == "alternate" }.href.toHttpUrl()
        url = postUrl.encodedPath + if (mirror) "#sv2" else ""
        name = title.text
        chapter_number = title.text.chapterNumber()
        date_upload = Instant.tryParse(published.text)
    }
}

private val titleCharacters = Regex("[^\\p{L}\\p{N}]")
private val combiningMarks = Regex("\\p{M}+")
private val chapterPattern = Regex("(?:chapter|ch\\.?|bab)\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)

fun String.normalizeTitle(): String = Normalizer.normalize(this, Normalizer.Form.NFKD)
    .replace(combiningMarks, "").lowercase().replace(titleCharacters, "")

private fun String.chapterNumber() = chapterPattern.find(this)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f

fun Element.imageUrl(): String? = absUrl("data-src").ifBlank { absUrl("src") }
    .takeIf { it.startsWith("https://") || it.startsWith("http://") }
