package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

private val CHAPTER_REGEX = """Chapter\s+(\d+(?:\.\d+)?)""".toRegex(RegexOption.IGNORE_CASE)

internal fun String.chapterLabel(): String? = CHAPTER_REGEX.find(this)?.value

internal fun String.chapterNumber(): Double = CHAPTER_REGEX.find(this)?.groupValues?.get(1)?.toDoubleOrNull() ?: -1.0

// Roman numerals mapped to digits so "Isekai Furin II" matches "isekai furin 2".
// Ordered longest-first to avoid partial replacements.
private val ROMAN_TO_DIGIT = listOf(
    "viii" to "8",
    "vii" to "7",
    "iii" to "3",
    "ii" to "2",
    "ix" to "9",
    "iv" to "4",
    "vi" to "6",
    "v" to "5",
    "x" to "10",
    "i" to "1",
)

internal fun String.normalized(): String {
    var s = lowercase()
    for ((roman, digit) in ROMAN_TO_DIGIT) {
        s = s.replace(Regex("\\b$roman\\b"), digit)
    }
    return s.filter { it.isLetterOrDigit() }
}

// Split into words (with Roman numerals normalized) for lenient title matching.
// Handles conversion variants like "konten" vs "tamashiten" where only
// one word differs between the catalog title and the Blogger post title.
internal fun String.titleWords(): List<String> {
    var s = lowercase()
    for ((roman, digit) in ROMAN_TO_DIGIT) {
        s = s.replace(Regex("\\b$roman\\b"), digit)
    }
    return s.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
}

internal fun titleWordsMatch(postTitle: String, mangaTitle: String): Boolean {
    val mangaWords = mangaTitle.titleWords()
    if (mangaWords.isEmpty()) return false
    val postWords = postTitle.titleWords().toSet()
    val matched = mangaWords.count { it in postWords }
    return when {
        mangaWords.size <= 2 -> matched == mangaWords.size
        else -> matched >= (mangaWords.size * 0.7).toInt()
    }
}

@Serializable
class CatalogEntry(
    val title: String,
    val slug: String,
    val altTitle: String = "",
    val img: String = "",
    val desc: String = "",
    val genres: List<String> = emptyList(),
    val rating: Double = 0.0,
    val status: String = "",
    val author: String = "",
    val artist: String = "",
) {
    fun toSManga() = SManga.create().apply {
        title = this@CatalogEntry.title
        thumbnail_url = img.takeIf { it.isNotBlank() }
        url = slug
    }
}

@Serializable
class ApiChaptersResponse(val chapters: List<ApiChapter> = emptyList())

@Serializable
class ApiChapter(
    val num: String,
    val title: String,
    val url: String,
    val images: List<String>,
    val thumbnail: String,
    val createdAt: String,
    val source: String,
)

// Firestore WebChannel Listen request, as sent by the site's own Firebase SDK.
@Serializable
class ListenRequest(
    val database: String,
    val addTarget: ListenAddTarget,
    val targetId: Int,
)

@Serializable
class ListenAddTarget(
    val query: ListenQuery,
    val parent: String,
)

@Serializable
class ListenQuery(
    val structuredQuery: ListenStructuredQuery,
)

@Serializable
class ListenStructuredQuery(
    val from: List<ListenCollectionRef>,
    val orderBy: List<ListenOrderBy>,
)

@Serializable
class ListenCollectionRef(
    val collectionId: String,
)

@Serializable
class ListenOrderBy(
    val field: ListenFieldRef,
    val direction: String,
)

@Serializable
class ListenFieldRef(
    val fieldPath: String,
)

@Serializable
class FirestoreMap(val fields: Map<String, FirestoreValue> = emptyMap())

@Serializable
class FirestoreDoc(
    val name: String = "",
    val fields: Map<String, FirestoreValue> = emptyMap(),
)

@Serializable
class FirestoreValue(
    val stringValue: String? = null,
    val integerValue: String? = null,
    val booleanValue: Boolean? = null,
    val arrayValue: FirestoreArray? = null,
    val mapValue: FirestoreMap? = null,
)

@Serializable
class FirestoreArray(val values: List<FirestoreValue> = emptyList())

internal fun Map<String, FirestoreValue>.getString(key: String): String? = get(key)?.stringValue

internal fun Map<String, FirestoreValue>.getStringList(key: String): List<String> = get(key)?.arrayValue?.values?.mapNotNull { it.stringValue }.orEmpty()

@Serializable
class BloggerFeedResponse(val feed: BloggerFeed)

@Serializable
class BloggerFeed(val entry: List<BloggerEntry> = emptyList())

@Serializable
class BloggerEntryResponse(val entry: BloggerEntry)

@Serializable
class BloggerText(@SerialName("\$t") val text: String)

@Serializable
class BloggerEntry(
    private val id: BloggerText,
    private val title: BloggerText,
    private val published: BloggerText? = null,
    private val content: BloggerText? = null,
) {
    fun chapterLabel(mangaTitle: String): String? {
        val postNorm = title.text.normalized()
        val mangaNorm = mangaTitle.normalized()
        // exact substring match
        if (postNorm.contains(mangaNorm)) return title.text.chapterLabel()
        // Fallback: word overlap for conversion variants (e.g. "konten" vs "tamashiten")
        if (titleWordsMatch(title.text, mangaTitle)) return title.text.chapterLabel()
        return null
    }

    fun imageUrls(): List<String> = Jsoup.parseBodyFragment(content?.text.orEmpty())
        .select("img[src^=http]")
        .map { it.attr("src") }
        .distinct()
}
