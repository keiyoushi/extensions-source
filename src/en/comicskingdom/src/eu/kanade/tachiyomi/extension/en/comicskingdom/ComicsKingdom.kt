package eu.kanade.tachiyomi.extension.en.comicskingdom

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class ComicsKingdom : ParsedHttpSource() {

    override val name = "Comics Kingdom"

    override val baseUrl = "https://comicskingdom.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaSelector() = ".comic-result-container .comic-card"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = ".posts-container .comic-card"

    override fun popularMangaNextPageSelector() = ".ck-pagination__item--next"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/comics?page=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/comics?page=$page&sortby=newest", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET(
            "$baseUrl/search".toHttpUrl().newBuilder().apply {
                addQueryParameter("key", query)
                addQueryParameter("page", page.toString())
                addQueryParameter("featurepage", page.toString())
                if (!filters.isEmpty()) {
                    for (filter in filters) {
                        when (filter) {
                            is SortFilter -> {
                                addQueryParameter("sortby", filter.getSortQuery())
                            }

                            is GenreList -> {
                                addQueryParameter("ck_genre", filter.included.joinToString(","))
                                addQueryParameter(
                                    "feature_genre",
                                    filter.included.joinToString(","),
                                )
                            }

                            else -> {}
                        }
                    }
                }
            }.build(),
            headers,
        )

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            setUrlWithoutDomain(
                element.selectFirst(".card-title__link")!!.attr("abs:href")
                    .substringBeforeLast("/"),
            )
            title = element.selectFirst(".card-title__link")!!.text()
            thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        status = SManga.UNKNOWN
        author = document.selectFirst(".creator-featured__name")?.text()
        description = document.selectFirst(".feature-about__description")?.text()
        thumbnail_url = document.selectFirst(".feature-header__media img")?.attr("abs:src")
    }

    private val json: Json by injectLazy()

    @Serializable
    private data class Chapter(
        val link: String,
        val date: String,
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaName = response.request.url.pathSegments.last()
        val chapters = mutableListOf<SChapter>()
        var pageNum = 1

        var chapterData = getChapterList(mangaName, pageNum)
        var chapterNum = -0.01F

        Log.i("comics", "handling page $pageNum for $mangaName")
        while (chapterData != null) {
            val list = chapterData.map {
                chapterNum += 0.01F
                SChapter.create().apply {
                    Log.i("comics", it.link)
                    chapter_number = chapterNum
                    setUrlWithoutDomain(it.link)
                    date_upload = dateFormat.parse(it.date).time
                    name = it.date.substringBefore("T")
                }
            }
            chapters.addAll(list)

            if (list.count() < 100) {
                Log.i("comics", "End of chapterParse")
                break
            }

            pageNum++
            chapterData = getChapterList(mangaName, pageNum)
        }

        return chapters
    }

    private fun getChapterList(manga: String, page: Int): List<Chapter> {
        val call =
            client.newCall(
                GET(
                    "https://wp.comicskingdom.com/wp-json/wp/v2/ck_comic?ck_feature=$manga&page=$page&per_page=100&order=desc&_fields=link,date",
                    headers,
                ),
            )
                .execute()
        val body = call.body.string()
        call.close()
        return json.decodeFromString<List<Chapter>>(body)
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter =
        throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        return listOf(
            Page(
                1,
                imageUrl = document.selectFirst(".ck-single-panel-reader img")!!
                    .attr("abs:src").toHttpUrl().queryParameter("url")!!.substringBefore("&"),
            ),
        )
    }

    private class SortFilter : Filter.Select<String>("Sort by", arrayOf("Newest First", "Oldest First")) {
        fun getSortQuery(): String {
            return if (state == 0) "newest" else "oldest"
        }
    }

    private class Genre(name: String, val gid: String) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres) {
        val included: List<String>
            get() = state.filter { it.state }.map { it.gid }
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        GenreList(getGenreList()),
    )

    override fun imageUrlParse(document: Document): String = ""

    // [...document.querySelectorAll(".search-checks li")].map((el) => `Genre("${el.innerText}", "${el.innerText.replaceAll(" ","+")}")`).join(',\n')
    // on https://readcomic.net/advanced-search
    private fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Classic", "classic"),
        Genre("Comedy", "comedy"),
        Genre("Crime", "crime"),
        Genre("Fantasy", "fantasy"),
        Genre("Gag Cartoons", "gag-cartoons"),
        Genre("Mystery", "mystery"),
        Genre("New Arrivals", "new-arrivals"),
        Genre("Non-Fiction", "non-fiction"),
        Genre("OffBeat", "offbeat"),
        Genre("Political Cartoons", "political-cartoons"),
        Genre("Romance", "romance"),
        Genre("Sci-Fi", "sci-fi"),
        Genre("Slice Of Life", "slice-of-life"),
        Genre("Superhero", "superhero"),
        Genre("Vintage", "vintage"),
    )
}
