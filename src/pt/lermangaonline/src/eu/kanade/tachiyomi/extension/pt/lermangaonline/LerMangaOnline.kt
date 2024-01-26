package eu.kanade.tachiyomi.extension.pt.lermangaonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class LerMangaOnline : ParsedHttpSource() {
    override val name = "Ler Manga Online"

    override val baseUrl = "https://lermangaonline.com.br"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 1, TimeUnit.SECONDS)
        .build()

    override fun chapterFromElement(element: Element): SChapter {
        TODO("Not yet implemented")
    }

    override fun chapterListSelector(): String {
        TODO("Not yet implemented")
    }

    override fun imageUrlParse(document: Document): String {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("section h3")!!.text()
        thumbnail_url = element.selectFirst("div.poster img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("div.poster a")!!.absUrl("href"))
    }

    override fun latestUpdatesNextPageSelector() = "div.pagenavi"

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/capitulo/page/$page".toHttpUrl().newBuilder()
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesSelector() = "div.box-indx section.materias article"

    override fun mangaDetailsParse(document: Document): SManga {
        TODO("Not yet implemented")
    }

    override fun pageListParse(document: Document): List<Page> {
        val elements = document.select("div.wp-pagenavi a.page, div.wp-pagenavi span")
        return elements.mapIndexed { i, el ->
            Page(i + 1, document.location(), el.srcAttr())
        }
    }

    override fun popularMangaFromElement(element: Element) =
        throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector() =
        throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int) =
        throw UnsupportedOperationException()

    override fun popularMangaSelector() =
        throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element): SManga {
        TODO("Not yet implemented")
    }

    override fun searchMangaNextPageSelector(): String? {
        TODO("Not yet implemented")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("Not yet implemented")
    }

    override fun searchMangaSelector(): String {
        TODO("Not yet implemented")
    }

    private fun Element.srcAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }
}
