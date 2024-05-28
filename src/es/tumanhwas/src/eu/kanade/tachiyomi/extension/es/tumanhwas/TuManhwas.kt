package eu.kanade.tachiyomi.extension.es.tumanhwas

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class TuManhwas : MangaThemesia(
    "TuManhwas",
    "https://tumanhwas.com",
    "es",
    "/biblioteca",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(mangaUrlDirectory.substring(1))
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return searchMangaFromElement(element).apply {
            val chapter = "-${url.substringAfterLast("-")}"
            url = url
                .replace("news", "manga")
                .removeSuffix(chapter)
        }
    }

    override fun latestUpdatesSelector(): String = ".bixbox.seriesearch:has(h1) .listupd .bs .bsx"

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map(::latestUpdatesFromElement)
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun searchMangaNextPageSelector() = " .page-link[aria-label~='Siguiente']"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(mangaUrlDirectory.substring(1))
            .addQueryParameter("search", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a")!!.let {
            title = it.selectFirst(".tt")!!.text()
            thumbnail_url = it.selectFirst("img")?.imgAttr()
            setUrlWithoutDomain(it.attr("href"))
        }
    }

    override fun getFilterList() = FilterList()
}
