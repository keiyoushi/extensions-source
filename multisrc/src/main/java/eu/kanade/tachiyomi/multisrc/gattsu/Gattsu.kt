package eu.kanade.tachiyomi.multisrc.gattsu

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

abstract class Gattsu(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = Headers.Builder()
        .add("Accept", ACCEPT)
        .add("Accept-Language", ACCEPT_LANGUAGE)
        .add("Referer", baseUrl)

    // Website does not have a popular, so use latest instead.
    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularMangaSelector(): String = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector(): String? = latestUpdatesNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        val path = if (page == 1) "" else "page/$page"
        return GET("$baseUrl/$path", headers)
    }

    override fun latestUpdatesSelector() = "div.meio div.lista ul li a[href^=$baseUrl]"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("span.thumb-titulo").first()!!.text()
        thumbnail_url = element.select("span.thumb-imagem img.wp-post-image").first()!!.attr("src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun latestUpdatesNextPageSelector(): String = "ul.paginacao li.next > a"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = "$baseUrl/page/$page/".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("post_type", "post")
            .toString()

        return GET(searchUrl, headers)
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector(): String = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val postBox = document.select("div.meio div.post-box").first()!!

        title = postBox.select("h1.post-titulo").first()!!.text()
        author = postBox.select("ul.post-itens li:contains(Artista) a").firstOrNull()?.text()
        genre = postBox.select("ul.post-itens li:contains(Tags) a")
            .joinToString(", ") { it.text() }
        description = postBox.select("div.post-texto p")
            .joinToString("\n\n") { it.text() }
            .replace("Sinopse :", "")
            .trim()
        status = SManga.COMPLETED
        thumbnail_url = postBox.select("div.post-capa > img.wp-post-image")
            .attr("src")
            .withoutSize()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        if (document.select(pageListSelector()).firstOrNull() == null) {
            return emptyList()
        }

        return document.select(chapterListSelector())
            .map { chapterFromElement(it) }
    }

    override fun chapterListSelector() = "div.meio div.post-box:first-of-type"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = "Capítulo único"
        scanlator = element.select("ul.post-itens li:contains(Tradutor) a").firstOrNull()?.text()
        date_upload = element.ownerDocument()!!.select("meta[property=article:published_time]").firstOrNull()
            ?.attr("content")
            .orEmpty()
            .toDate()
        setUrlWithoutDomain(element.ownerDocument()!!.location())
    }

    protected open fun pageListSelector(): String =
        "div.meio div.post-box ul.post-fotos li a > img, " +
            "div.meio div.post-box.listaImagens div.galeriaHtml img"

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListSelector())
            .mapIndexed { i, el ->
                Page(i, document.location(), el.imgAttr().withoutSize())
            }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .add("Accept", ACCEPT_IMAGE)
            .add("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    protected fun Element.imgAttr(): String =
        if (hasAttr("data-src")) {
            attr("abs:data-src")
        } else {
            attr("abs:src")
        }

    protected fun String.toDate(): Long {
        return try {
            DATE_FORMATTER.parse(this.substringBefore("T"))?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    protected fun String.withoutSize(): String = this.replace(THUMB_SIZE_REGEX, ".")

    companion object {
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9," +
            "image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"

        private val THUMB_SIZE_REGEX = "-\\d+x\\d+\\.".toRegex()

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        }
    }
}
