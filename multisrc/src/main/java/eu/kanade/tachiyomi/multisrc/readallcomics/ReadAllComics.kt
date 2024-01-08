package eu.kanade.tachiyomi.multisrc.readallcomics

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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable

abstract class ReadAllComics(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    override val supportsLatest = false

    private lateinit var searchPageElements: Elements

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::archivedCategoryInterceptor)
        .rateLimit(2)
        .build()

    protected open fun archivedCategoryInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val document = Jsoup.parse(
            response.peekBody(Long.MAX_VALUE).string(),
            request.url.toString(),
        )

        val newUrl = document.selectFirst(archivedCategorySelector())
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

    protected open fun archivedCategorySelector() = ".description-archive > p > span > a"

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl${if (page > 1)"/page/$page/" else ""}", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).mapNotNull {
            nullablePopularManga(it)
        }

        val hasNextPage = document.select(popularMangaNextPageSelector()).first() != null

        return MangasPage(mangas, hasNextPage)
    }

    protected open fun nullablePopularManga(element: Element): SManga? {
        val manga = SManga.create().apply {
            val category = element.classNames()
                .firstOrNull { it.startsWith("category-") }
                ?.substringAfter("category-")
                ?: return null

            url = "/category/$category/"
            title = element.select(popularMangaTitleSelector()).text()
            thumbnail_url = element.select(popularMangaThumbnailSelector()).attr("abs:src")
        }

        return manga
    }

    override fun popularMangaSelector() = "#post-area > div"
    override fun popularMangaNextPageSelector() = "div.pagenavi > a.next"
    protected open fun popularMangaTitleSelector() = "h2"
    protected open fun popularMangaThumbnailSelector() = "img"

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { searchMangaParse(it) }
        } else {
            Observable.just(searchPageParse(page))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/?story=${query.trim()}&s=&type=${searchType()}", headers)

    protected open fun searchType() = "comic"

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
        title = element.text().trim()
        thumbnail_url = searchCover
    }

    override fun searchMangaSelector() = ".categories a"
    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select(mangaDetailsTitleSelector()).text().trim()
        genre = document.select(mangaDetailsGenreSelector()).joinToString { it.text().trim() }
        author = document.select(mangaDetailsAuthorSelector()).last()?.text()?.trim()
        description = document.select(mangaDetailsDescriptionSelector()).text().trim()
        thumbnail_url = document.select(mangaDetailsThumbnailSelector()).attr("abs:src")
    }

    protected open fun mangaDetailsTitleSelector() = "h1"
    protected open fun mangaDetailsGenreSelector() = "p strong"
    protected open fun mangaDetailsAuthorSelector() = "p > strong"
    protected open fun mangaDetailsDescriptionSelector() = ".b > strong"
    protected open fun mangaDetailsThumbnailSelector() = "p img"

    override fun chapterListSelector() = ".list-story a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.attr("title")
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListSelector()).mapIndexed { idx, element ->
            Page(idx, "", element.attr("abs:src"))
        }
    }

    protected open fun pageListSelector() = "body > div img"

    companion object {
        private const val searchCover = "https://fakeimg.pl/200x300/?text=No%20Cover%0AOn%20Search&font_size=62"
    }

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("Not Implemented")
    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("Not Implemented")
    override fun latestUpdatesFromElement(element: Element) =
        throw UnsupportedOperationException("Not Implemented")
    override fun latestUpdatesSelector() =
        throw UnsupportedOperationException("Not Implemented")
    override fun latestUpdatesNextPageSelector() =
        throw UnsupportedOperationException("Not Implemented")
    override fun popularMangaFromElement(element: Element) =
        throw UnsupportedOperationException("Not Implemented")
}
