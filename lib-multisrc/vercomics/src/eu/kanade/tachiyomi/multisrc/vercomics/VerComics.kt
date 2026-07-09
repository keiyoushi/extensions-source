package eu.kanade.tachiyomi.multisrc.vercomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

abstract class VerComics : HttpSource() {

    override val supportsLatest: Boolean = false

    protected open val urlSuffix = ""
    protected open val genreSuffix = ""
    protected open val useSuffixOnSearch = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$urlSuffix/page/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun popularMangaSelector() = "header:has(h1) ~ * .entry"

    protected open fun popularMangaNextPageSelector() = "div.wp-pagenavi > span.current + a"

    protected open fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a.popimg")?.let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.selectFirst("img")?.attr("alt") ?: ""
            thumbnail_url = it.selectFirst("img:not(noscript img)")?.imgAttr()
        }
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotEmpty()) {
            url = baseUrl.toHttpUrl().newBuilder()
            if (useSuffixOnSearch) {
                url.addPathSegments(urlSuffix)
            }
            url.addPathSegments("page")
            url.addPathSegments(page.toString())
            url.addQueryParameter("s", query)

            return GET(url.build(), headers)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val uriPart = filter.toUriPart()
                    if (uriPart.isNotEmpty()) {
                        url.addPathSegments(genreSuffix)
                        url.addPathSegments(uriPart)
                        url.addPathSegments("page")
                        url.addPathSegments(page.toString())
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun searchMangaSelector() = popularMangaSelector()

    protected open fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    protected open fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        document.selectFirst("div.tax_post")?.let {
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            val genreList = document.select("div.tax_box:has(div.title:contains(Etiquetas)) a[rel=tag]")
            genre = genreList.joinToString { genre ->
                val text = genre.text().replaceFirstChar { it.uppercase() }
                val slug = genre.attr("href").substringAfter("$baseUrl/$genreSuffix/").removeSuffix("/")
                val newPair = Pair(text, slug)

                if (!genres.contains(newPair)) {
                    genres += newPair
                }

                text
            }
        }
    }

    // ============================= Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.just(
        listOf(
            SChapter.create().apply {
                name = manga.title
                url = manga.url
            },
        ),
    )

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ===============================
    protected open val pageListSelector =
        "div.wp-content p > img:not(noscript img), " +
            "div.wp-content div#lector > img:not(noscript img), " +
            "div.wp-content > figure img:not(noscript img), " +
            "div.wp-content > img, div.wp-content > p img, " +
            "div.post-imgs > img"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(pageListSelector).mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr() ?: img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    protected open fun Element.imgAttr(): String? = when {
        this.hasAttr("data-src") -> this.attr("abs:data-src")
        this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
        this.hasAttr("srcset") -> this.attr("abs:srcset").getSrcSetImage()
        this.hasAttr("data-cfsrc") -> this.attr("abs:data-cfsrc")
        else -> this.attr("abs:src")
    }

    private fun String.getSrcSetImage(): String? = this.split(" ")
        .filter { URL_REGEX.matches(it) }
        .maxOfOrNull { it }

    // ============================== Filters ==============================
    protected open var genres = arrayOf(Pair("Ver todos", ""))

    override fun getFilterList(): FilterList {
        val filters = listOf(
            Filter.Header("Los filtros serán ignorados si la búsqueda no está vacía."),
            Filter.Separator(),
            GenreFilter(genres),
        )

        return FilterList(filters)
    }

    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toUriPart() = vals[state].second
    }

    class GenreFilter(genres: Array<Pair<String, String>>) : UriPartFilter("Filtrar por género", genres)

    companion object {
        private val URL_REGEX = """^(https?://[^\s/$.?#].\S*)$""".toRegex()
    }
}
