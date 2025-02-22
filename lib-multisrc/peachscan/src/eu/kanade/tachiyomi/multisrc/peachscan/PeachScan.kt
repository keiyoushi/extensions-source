package eu.kanade.tachiyomi.multisrc.peachscan

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.lib.zipinterceptor.ZipInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@SuppressLint("WrongConstant")
abstract class PeachScan(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy 'às' HH:mm", Locale("pt", "BR")).apply {
        timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
    },
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient
        .newBuilder()
        .addInterceptor(ZipInterceptor()::zipImageInterceptor)
        .build()

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/todas-as-obras/", headers)

    override fun popularMangaSelector() = ".comics__all__box"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val anchor = element.selectFirst(".titulo__comic__allcomics")!!

        setUrlWithoutDomain(anchor.attr("href"))
        title = anchor.text()
        thumbnail_url = element.selectFirst(".box-image img")?.attr("abs:src")
    }

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "div.comic:not(:has(a.box-image > p:contains(Novel)))"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        title = element.selectFirst(".titulo__comic")!!.text()
        thumbnail_url = element.selectFirst(".comic__img")?.attr("abs:src")
    }

    override fun latestUpdatesNextPageSelector() = null

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val manga = SManga.create().apply { url = query.substringAfter(URL_SEARCH_PREFIX) }
            return client.newCall(mangaDetailsRequest(manga))
                .asObservableSuccess()
                .map {
                    MangasPage(listOf(mangaDetailsParse(it).apply { url = manga.url }), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("auto-complete/")
            addQueryParameter("term", query)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val manga = json.parseToJsonElement(response.body.string()).jsonArray.mapNotNull {
            val element = Jsoup.parseBodyFragment(it.jsonObject["html"]!!.jsonPrimitive.content)

            runCatching { searchMangaFromElement(element) }.getOrNull()
        }

        return MangasPage(manga, false)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))

        title = element.selectFirst("span")!!.text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".desc__titulo__comic")!!.text()
        author = document.selectFirst(".sumario__specs__box:contains(Autor) + .sumario__specs__tipo")?.text()
        genre = document.select("a[href*=pesquisar?category]").joinToString { it.text() }
        status = when (document.selectFirst(".sumario__specs__box:contains(Status) + .sumario__specs__tipo")?.text()) {
            "Em Lançamento" -> SManga.ONGOING
            "Finalizado" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst(".sumario__img")?.attr("abs:src")

        val category = document.selectFirst(".categoria__comic")?.text()
        val synopsis = document.selectFirst(".sumario__sinopse__texto")?.text()
        description = "Tipo: $category\n\n$synopsis"
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mediaType = document.selectFirst(".categoria__comic")?.text()

        if (mediaType == "Novel") {
            // Google translated, sorry
            throw Exception("Novelas não podem ser lidos em Tachiyomi, acesse o site")
        }

        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    override fun chapterListSelector() = ".link__capitulos"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))

        name = element.selectFirst(".numero__capitulo")!!.text()
        date_upload = runCatching {
            val date = element.selectFirst(".data__lançamento")!!.text()

            dateFormat.parse(date)!!.time
        }.getOrDefault(0L)
    }

    private val urlsRegex = """const\s+urls\s*=\s*\[(.*?)]\s*;""".toRegex()

    override fun pageListParse(document: Document): List<Page> {
        val scriptElement = document.selectFirst("script:containsData(const urls)")
            ?: return document.select("#imageContainer img").mapIndexed { i, it ->
                Page(i, document.location(), it.attr("abs:src"))
            }

        val urls = urlsRegex.find(scriptElement.data())?.groupValues?.get(1)
            ?: throw Exception("Could not find image URLs")

        return urls.split(",").mapIndexed { i, it ->
            Page(i, document.location(), baseUrl + it.trim().removeSurrounding("'") + "#page")
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .add("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, imgHeaders)
    }

    companion object {
        const val URL_SEARCH_PREFIX = "slug:"
    }
}
