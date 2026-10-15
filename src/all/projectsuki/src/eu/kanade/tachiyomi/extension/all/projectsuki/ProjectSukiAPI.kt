package eu.kanade.tachiyomi.extension.all.projectsuki

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.network.post
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 *  @see EXTENSION_INFO Found in ProjectSuki.kt
 */
@Suppress("unused")
private inline val INFO: Nothing get() = error("INFO")

internal val callpageUrl = homepageUrl.newBuilder().addPathSegment("callpage").build()
internal val apiBookSearchUrl = homepageUrl.newBuilder()
    .addPathSegment("api")
    .addPathSegment("book")
    .addPathSegment("search")
    .build()

private val imageExtensions = setOf(".jpg", ".png", ".jpeg", ".webp", ".gif", ".avif", ".tiff")
private val simpleSrcVariants = listOf("src", "data-src", "data-lazy-src")

/**
 * Function that tries to extract the image URL from some known ways to store that information.
 */
fun Element.imageSrc(): HttpUrl? {
    simpleSrcVariants.forEach { variant ->
        if (hasAttr(variant)) {
            return attr("abs:$variant").toHttpUrlOrNull()
        }
    }

    if (hasAttr("srcset")) {
        return attr("abs:srcset").substringBefore(" ").toHttpUrlOrNull()
    }

    return attributes().firstOrNull {
        it.key.contains("src") && imageExtensions.any { ext -> it.value.contains(ext) }
    }?.key?.let { absUrl(it) }?.substringBefore(" ")?.toHttpUrlOrNull()
}

internal typealias BookTitle = String

/**
 * Singleton responsible for handling API communications with Project Suki's server.
 *
 * @author Federico d'Alonzo &lt;me@npgx.dev&gt;
 */
object ProjectSukiAPI {

    /**
     * Represents the data that needs to be sent to [callpageUrl] to obtain the pages of a chapter.
     */
    @Serializable
    class PagesRequestData(
        @SerialName("bookid") private val bookID: BookID,
        @SerialName("chapterid") private val chapterID: ChapterID,
        private val first: String,
    ) {
        init {
            if (first != "true" && first != "false") {
                reportErrorToUser { "PagesRequestData, first was \"$first\"" }
            }
        }
    }

    @Serializable
    class ChapterPagesResponse(
        val src: String,
    )

    /**
     * Requests the chapter's pages from the server.
     */
    suspend fun fetchChapterPages(client: OkHttpClient, headers: Headers, bookID: BookID, chapterID: ChapterID): List<Page> {
        val newHeaders: Headers = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val body = PagesRequestData(bookID, chapterID, "true").toJsonRequestBody()
        return parseChapterPagesResponse(client.post(callpageUrl, newHeaders, body))
    }

    private fun parseChapterPagesResponse(response: Response): List<Page> {
        val dto = response.parseAs<ChapterPagesResponse>()
        val rawSrc = dto.src

        val srcFragment: Element = Jsoup.parseBodyFragment(rawSrc, homepageUri.toASCIIString())

        val urls: Map<HttpUrl, PathMatchResult> = srcFragment.allElements
            .asSequence()
            .mapNotNull { it.imageSrc() }
            .associateWith { it.matchAgainst(pageUrlPattern) }
            .filterValues { it.doesMatch }

        if (urls.isEmpty()) {
            reportErrorToUser { "chapter pages URLs aren't in the expected format!" }
        }

        return urls.entries
            .sortedBy { (_, match) -> match.group(4)!!.toUInt() }
            .mapIndexed { index, (url, _) ->
                Page(
                    index = index,
                    url = "",
                    imageUrl = url.toUri().toASCIIString(),
                )
            }
    }

    /** Represents the data that needs to be sent to [apiBookSearchUrl] to obtain the complete list of books that have chapters. */
    @Serializable
    class SearchRequestData(
        private val hash: String? = null,
    )

    @Serializable
    class SearchResponseData(
        val data: Map<String, SearchBookData> = emptyMap(),
    )

    @Serializable
    class SearchBookData(
        val value: String,
    )

    /**
     * Requests the complete list of books from the server.
     */
    suspend fun fetchBookSearch(client: OkHttpClient, headers: Headers): Map<BookID, BookTitle> {
        val newHeaders: Headers = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .set("Referer", homepageUrl.newBuilder().addPathSegment("browse").build().toUri().toASCIIString())
            .build()

        val body = SearchRequestData(null).toJsonRequestBody()
        return parseBookSearchResponse(client.post(apiBookSearchUrl, newHeaders, body))
    }

    private fun parseBookSearchResponse(response: Response): Map<BookID, BookTitle> {
        val dto = response.parseAs<SearchResponseData>()
        return dto.data.mapValues { it.value.value }
    }
}

private val alphaNumericRegex = """\p{Alnum}+""".toRegex(RegexOption.IGNORE_CASE)

/**
 * Creates a [MangasPage] containing a sorted list of mangas from best match to words.
 */
internal fun Map<BookID, BookTitle>.simpleSearchMangasPage(searchQuery: String): MangasPage {
    data class Match(val bookID: BookID, val title: BookTitle, val count: Int)

    val words: Set<String> = alphaNumericRegex.findAll(searchQuery).mapTo(HashSet()) { it.value }

    val matches: Map<BookID, Match> = mapValues { (bookID, bookTitle) ->
        val matchesCount: Int = words.sumOf { word ->
            var count = 0
            var idx = 0

            while (true) {
                val found = bookTitle.indexOf(word, idx, ignoreCase = true)
                if (found < 0) break

                idx = found + 1
                count++
            }

            count
        }

        Match(bookID, bookTitle, matchesCount)
    }.filterValues { it.count > 0 }

    return matches.entries
        .sortedWith(compareBy({ -it.value.count }, { it.value.title }))
        .associate { (bookID, match) -> bookID to match.title }
        .toMangasPage()
}
