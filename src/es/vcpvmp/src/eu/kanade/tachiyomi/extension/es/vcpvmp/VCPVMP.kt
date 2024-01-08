package eu.kanade.tachiyomi.extension.es.vcpvmp

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

open class VCPVMP(override val name: String, override val baseUrl: String) : ParsedHttpSource() {

    override val lang = "es"

    override val supportsLatest: Boolean = false

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("Referer", "$baseUrl/")
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$urlSuffix/page/$page", headers)

    override fun popularMangaSelector() = "div.blog-list-items > div.entry"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a.popimg").first()!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.select("img").attr("alt")
            thumbnail_url = it.select("img:not(noscript img)").attr("abs:data-src")
        }
    }

    override fun popularMangaNextPageSelector() = "div.wp-pagenavi > span.current + a"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.select("div.tax_post").let {
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            val genreList = document.select("div.tax_box:has(div.title:contains(Etiquetas)) a[rel=tag]")
            genre = genreList.joinToString { genre ->
                val text = genre.text().replaceFirstChar { it.uppercase() }
                val slug = genre.attr("href").replace("$baseUrl/$genreSuffix/", "")
                val newPair = Pair(text, slug)

                if (!genres.contains(newPair)) {
                    genres += newPair
                }

                text
            }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = manga.title
                    url = manga.url
                },
            ),
        )
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    protected open val pageListSelector = "div.wp-content p > img:not(noscript img)"
    override fun pageListParse(document: Document): List<Page> = document.select(pageListSelector)
        .mapIndexed { i, img -> Page(i, "", img.attr("abs:data-src")) }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    protected open val urlSuffix = ""
    protected open val genreSuffix = ""

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = baseUrl.toHttpUrlOrNull()!!.newBuilder()

        if (query.isNotBlank()) {
            url = "$baseUrl/$urlSuffix".toHttpUrlOrNull()!!.newBuilder()
            url.addPathSegments("page")
            url.addPathSegments(page.toString())
            url.addQueryParameter("s", query)

            return GET(url.build().toString(), headers)
        }

        filters.forEach { filter ->
            when (filter) {
                is Genre -> {
                    if (filter.toUriPart().isNotEmpty()) {
                        url.addPathSegments(genreSuffix)
                        url.addPathSegments(filter.toUriPart())

                        url.addPathSegments("page")
                        url.addPathSegments(page.toString())
                    }
                }
                else -> {}
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    protected open var genres = arrayOf(Pair("Ver todos", ""))

    override fun getFilterList(): FilterList {
        val filters = listOf(
            Filter.Header("Los filtros serán ignorados si la búsqueda no está vacía."),
            Filter.Separator(),
            Genre(genres),
        )

        return FilterList(filters)
    }

    // Array.from(document.querySelectorAll('div.tagcloud a.tag-cloud-link')).map(a => `Pair("${a.innerText}", "${a.href.replace('https://vercomicsporno.com/etiquetas/', '')}")`).join(',\n')
    // from https://vercomicsporno.com/

    private class Genre(genres: Array<Pair<String, String>>) : UriPartFilter(
        "Filtrar por género",
        genres,
    )
}
