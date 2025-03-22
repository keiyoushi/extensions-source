package eu.kanade.tachiyomi.multisrc.vercomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

abstract class VerComics(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    override val supportsLatest: Boolean = false

    protected open val urlSuffix = ""
    protected open val genreSuffix = ""
    protected open val useSuffixOnSearch = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$urlSuffix/page/$page", headers)

    override fun popularMangaSelector() = "header:has(h1) ~ * .entry"

    override fun popularMangaNextPageSelector() = "div.wp-pagenavi > span.current + a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a.popimg").first()!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.select("img").attr("alt")
            thumbnail_url = it.selectFirst("img:not(noscript img)")?.imgAttr()
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
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

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

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

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    protected open val pageListSelector =
        "div.wp-content p > img:not(noscript img), " +
            "div.wp-content div#lector > img:not(noscript img), " +
            "div.wp-content > figure img:not(noscript img)," +
            "div.wp-content > img, div.wp-content > p img"

    override fun pageListParse(document: Document): List<Page> = document.select(pageListSelector)
        .mapIndexed { i, img -> Page(i, imageUrl = img.imgAttr()) }

    protected open var genres = arrayOf(Pair("Ver todos", ""))

    override fun getFilterList(): FilterList {
        val filters = listOf(
            Filter.Header("Los filtros serán ignorados si la búsqueda no está vacía."),
            Filter.Separator(),
            Genre(genres),
        )

        return FilterList(filters)
    }

    protected open fun Element.imgAttr(): String? {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").getSrcSetImage()
            this.hasAttr("data-cfsrc") -> this.attr("abs:data-cfsrc")
            else -> this.attr("abs:src")
        }
    }

    private fun String.getSrcSetImage(): String? {
        return this.split(" ")
            .filter(URL_REGEX::matches)
            .maxOfOrNull(String::toString)
    }

    // Replace the baseUrl and genreSuffix in the following string
    // Array.from(document.querySelectorAll('div.tagcloud a.tag-cloud-link')).map(a => `Pair("${a.innerText}", "${a.href.replace('$baseUrl/genreSuffix/', '')}")`).join(',\n')
    class Genre(genres: Array<Pair<String, String>>) : UriPartFilter(
        "Filtrar por género",
        genres,
    )

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    companion object {
        val URL_REGEX = """^(https?://[^\s/$.?#].[^\s]*)${'$'}""".toRegex()
    }

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
