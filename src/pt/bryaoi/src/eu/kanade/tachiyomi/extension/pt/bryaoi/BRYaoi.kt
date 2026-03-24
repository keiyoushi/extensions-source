package eu.kanade.tachiyomi.extension.pt.bryaoi

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class BRYaoi : HttpSource() {

    override val name = "BR Yaoi"

    override val baseUrl = "https://bryaoi.com"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client = network.cloudflareClient

    // ====================== Popular ===============================

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ====================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // ====================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/yaoi/page/$page".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".listagem .item a").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h2")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
        return MangasPage(mangas, hasNextPage = document.selectFirst(".next.page-numbers") != null)
    }

    // ====================== Details ================================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
            .substringAfter("Ler").substringBeforeLast("Online")
            .trim()
        thumbnail_url = document.selectFirst(".serie-capa img")?.absUrl("src")
        description = document.select(".serie-texto p").joinToString("\n") { it.text() }
        genre = document.select(".serie-infos a").joinToString { it.text() }

        setUrlWithoutDomain(document.location())
    }

    // ====================== Chapters ================================

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select(".capitulos a").map { element ->
        SChapter.create().apply {
            name = element.text()
            setUrlWithoutDomain(element.absUrl("href"))
        }
    }.reversed()

    // ====================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.select("script[type*=javascript]:not([defer])")
            .map(Element::data)
            .firstOrNull { it.contains("imageArray") }
            ?: return emptyList()

        val json = QuickJs.create().use {
            it.evaluate("$script; imageArray")
        } as String

        return json.parseAs<PageDto>().images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response) = ""
}

@Serializable
class PageDto(
    val images: List<String>,
)
