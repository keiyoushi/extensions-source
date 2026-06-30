package eu.kanade.tachiyomi.extension.en.mangablaze

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class MangaBlaze : Madara() {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$mangaSubString/${searchPage(page)}?orderby=popular", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/$mangaSubString/${searchPage(page)}?orderby=new", headers)

    override fun popularMangaSelector() = "a.acard"
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst(".ac-t")!!.text()
        element.selectFirst(".ac-img img")?.let {
            thumbnail_url = processThumbnail(imageFromElement(it), true)
        }
    }

    override fun popularMangaNextPageSelector() = ".pager span.on + a"

    // The site filters by a single genre through the manga archive (?genre=slug)
    // instead of Madara's default genre[] parameter on the search endpoint.
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isBlank()) {
            val genre = filters.filterIsInstance<GenreList>()
                .firstOrNull()
                ?.state?.firstOrNull { it.state }?.id
            if (!genre.isNullOrEmpty()) {
                return GET("$baseUrl/$mangaSubString/${searchPage(page)}?genre=$genre", headers)
            }
        }
        return super.searchMangaRequest(page, query, filters)
    }

    override fun genresRequest() = GET("$baseUrl/$mangaSubString/", headers)

    override fun parseGenres(document: Document): List<Genre> = document.select(".chips a.chip[href*=genre=]").mapNotNull { a ->
        val slug = a.absUrl("href").toHttpUrlOrNull()?.queryParameter("genre") ?: return@mapNotNull null
        Genre(a.text(), slug)
    }

    override val mangaDetailsSelectorTitle = "h1.htitle"
    override val mangaDetailsSelectorThumbnail = ".poster img"
    override val mangaDetailsSelectorDescription = ".syn"
    override val mangaDetailsSelectorStatus = ".hinfo .hi.ok"
    override val mangaDetailsSelectorGenre = ".genres a.genre"

    // The detail page no longer embeds the chapter list nor the
    // manga-chapters-holder wrapper that Madara relies on to trigger the
    // AJAX fetch, so request the chapters endpoint directly.
    override fun chapterListRequest(manga: SManga) = xhrChaptersRequest(baseUrl + manga.url.removeSuffix("/"))

    // The endpoint returns classic Madara markup; premium chapters are
    // locked (href="#") so they are excluded.
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select(chapterListSelector()).map(::chapterFromElement)
}
