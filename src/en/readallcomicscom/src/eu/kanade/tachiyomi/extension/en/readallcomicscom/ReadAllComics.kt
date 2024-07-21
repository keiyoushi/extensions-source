package eu.kanade.tachiyomi.extension.en.readallcomicscom

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable

class ReadAllComics : ParsedHttpSource() {

    override val name = "ReadAllComics"

    override val baseUrl = "https://readallcomics.com"

    override val lang = "en"

    override val supportsLatest = false

    private lateinit var searchPageElements: Elements

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::archivedCategoryInterceptor)
        .rateLimit(2)
        .build()

    private fun archivedCategoryInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val document = Jsoup.parse(
            response.peekBody(Long.MAX_VALUE).string(),
            request.url.toString(),
        )

        val newUrl = document.selectFirst(".description-archive > p > span > a")
            ?.attr("href")?.toHttpUrlOrNull()
            ?: return response

        if (newUrl.pathSegments.contains("category")) {
            response.close()

            return chain.proceed(
                request.newBuilder()
                    .url(newUrl)
                    .build(),
            )
        }

        return response
    }

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl${if (page > 1)"/page/$page/" else ""}", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create().apply {
            val category = element.classNames()
                .firstOrNull { it.startsWith("category-") }!!
                .substringAfter("category-")

            url = "/category/$category/"
            title = category.replace("-", " ").capitalizeEachWord()
            thumbnail_url = element.select("img").attr("abs:src")
        }

        return manga
    }

    override fun popularMangaSelector() = "#post-area > div"
    override fun popularMangaNextPageSelector() = "div.pagenavi > a.next"

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { searchMangaParse(it) }
        } else {
            Observable.just(searchPageParse(page))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("story", query)
            addQueryParameter("s", "")
            addQueryParameter("type", "comic")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        searchPageElements = response.asJsoup().select(searchMangaSelector())

        return searchPageParse(1)
    }

    private fun searchPageParse(page: Int): MangasPage {
        val mangas = mutableListOf<SManga>()
        val endRange = ((page * 24) - 1).let { if (it <= searchPageElements.lastIndex) it else searchPageElements.lastIndex }

        for (i in (((page - 1) * 24)..endRange)) {
            mangas.add(
                searchMangaFromElement(searchPageElements[i]),
            )
        }

        return MangasPage(mangas, endRange < searchPageElements.lastIndex)
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.text()
        thumbnail_url = "https://fakeimg.pl/200x300/?text=No%20Cover%0AOn%20Search&font_size=62"
    }

    override fun searchMangaSelector() = ".categories a"
    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        genre = document.select("p strong").joinToString { it.text() }
        author = document.select("p > strong").last()?.text()
        description = buildString {
            document.select(".b > strong").forEach { element ->
                val vol = element.previousElementSibling()
                if (isNotBlank()) {
                    append("\n\n")
                }
                if (vol?.tagName() == "span") {
                    append(vol.text(), "\n")
                }
                append(element.text())
            }
        }
        thumbnail_url = document.select("p img").attr("abs:src")
    }

    override fun chapterListSelector() = ".list-story a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.attr("title")
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("body img:not(body div[id=\"logo\"] img)").mapIndexed { idx, element ->
            Page(idx, "", element.attr("abs:src"))
        }
    }

    private fun String.capitalizeEachWord(): String {
        val result = StringBuilder(length)
        var capitalize = true
        for (char in this) {
            result.append(
                if (capitalize) {
                    char.uppercase()
                } else {
                    char.lowercase()
                },
            )
            capitalize = char.isWhitespace()
        }
        return result.toString()
    }

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) =
        throw UnsupportedOperationException()
    override fun latestUpdatesSelector() =
        throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() =
        throw UnsupportedOperationException()
}
