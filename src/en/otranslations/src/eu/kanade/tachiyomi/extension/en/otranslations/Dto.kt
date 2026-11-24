package eu.kanade.tachiyomi.extension.en.otranslations

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
class SeriesEntry(
    val title: String,
    val slug: String,
    private val subtitle: String? = null,
    private val origin: String? = null,
    private val cover: String? = null,
    private val genres: List<String>? = null,
    private val tags: List<String>? = null,
    private val authors: List<String>? = null,
    private val artists: List<String>? = null,
) {
    // Site search score of one lowercase query word, 0 when it doesn't match
    fun score(word: String): Int {
        val title = title.lowercase()
        return when {
            title == word -> 100
            title.startsWith(word) -> 60
            word in title.searchWords() -> 50
            word in title -> 30
            subtitle?.lowercase()?.contains(word) == true -> 20
            (authors.orEmpty() + artists.orEmpty()).any { word in it.lowercase() } -> 15
            (genres.orEmpty() + tags.orEmpty() + listOfNotNull(origin)).any { word in it.lowercase() } -> 10
            else -> 0
        }
    }

    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = slug
        title = this@SeriesEntry.title
        thumbnail_url = cover?.let { baseUrl.resolve(it)?.toString() }
    }
}

private val wordSeparator = Regex("""[^\p{L}\p{N}]+""")

internal fun String.searchWords(): List<String> = lowercase().split(wordSeparator).filter { it.isNotEmpty() }

@Serializable
class Trending(val series: List<TrendingEntry>)

@Serializable
class TrendingEntry(val series: String)

@Serializable
class ReaderProps(val chapter: ReaderChapter)

@Serializable
class ReaderChapter(val pages: List<String>)
