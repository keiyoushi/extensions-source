package eu.kanade.tachiyomi.extension.pt.opex

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.util.Locale
import java.util.concurrent.TimeUnit

class OnePieceEx : ParsedHttpSource() {

    override val name = "One Piece Ex"

    override val baseUrl = "https://onepieceex.net"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(::bypassHttp103Intercept)
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", ACCEPT)
        .add("Accept-Language", ACCEPT_LANGUAGE)
        .add("Referer", "$baseUrl/mangas")

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/mangas", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangaPage = super.popularMangaParse(response)

        val mainManga = SManga.create().apply {
            title = "One Piece"
            thumbnail_url = MAIN_SERIES_THUMBNAIL
            url = "/mangas/?type=main"
        }

        val sbsManga = SManga.create().apply {
            title = "SBS"
            thumbnail_url = DEFAULT_THUMBNAIL
            url = "/mangas/?type=sbs"
        }

        val allMangas = listOf(mainManga, sbsManga) + mangaPage.mangas.toMutableList()

        return MangasPage(allMangas, mangaPage.hasNextPage)
    }

    override fun popularMangaSelector(): String = "#post > div.volume"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.volume-nome h2").text() + " - " +
            element.select("div.volume-nome h3").text()
        thumbnail_url = THUMBNAIL_URL_MAP[title.uppercase(Locale.ROOT)] ?: DEFAULT_THUMBNAIL

        val customUrl = "$baseUrl/mangas/".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("type", "special")
            .addQueryParameter("title", title)
            .toString()

        setUrlWithoutDomain(customUrl)
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters)
            .map { mangaPage ->
                val filteredMangas = mangaPage.mangas.filter { m -> m.title.contains(query, true) }
                MangasPage(filteredMangas, mangaPage.hasNextPage)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsRequest(manga: SManga): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/")
            .build()

        return GET(baseUrl + manga.url, newHeaders)
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val mangaUrl = document.location().toHttpUrlOrNull()!!

        when (mangaUrl.queryParameter("type")!!) {
            "main" -> {
                title = "One Piece"
                author = "Eiichiro Oda"
                genre = "Ação, Aventura, Comédia, Fantasia, Superpoderes"
                status = SManga.ONGOING
                description = "Um romance marítimo pelo \"One Piece\"!!! Estamos na Grande " +
                    "Era dos Piratas. Nela, muitos piratas lutam pelo tesouro deixado pelo " +
                    "lendário Rei dos Piratas G. Roger, o \"One Piece\". Luffy, um garoto " +
                    "que almeja ser pirata, embarca numa jornada com o sonho de se tornar " +
                    "o Rei dos Piratas!!! (Fonte: MANGA Plus)"
                thumbnail_url = MAIN_SERIES_THUMBNAIL
            }
            "sbs" -> {
                title = "SBS"
                author = "Eiichiro Oda"
                description = "O SBS é uma coluna especial encontrada na maioria dos " +
                    "tankobons da coleção, começando a partir do volume 4. É geralmente " +
                    "formatada como uma coluna direta de perguntas e respostas, com o " +
                    "Eiichiro Oda respondendo as cartas de fãs sobre uma grande variedade " +
                    "de assuntos. (Fonte: One Piece Wiki)"
                thumbnail_url = DEFAULT_THUMBNAIL
            }
            "special" -> {
                title = mangaUrl.queryParameter("title")!!

                val volumeEl = document.select("#post > div.volume:contains(" + title.substringAfter(" - ") + ")").first()!!
                author = if (title.contains("One Piece")) "Eiichiro Oda" else "OPEX"
                description = volumeEl.select("li.resenha").text()
                thumbnail_url = THUMBNAIL_URL_MAP[title.uppercase(Locale.ROOT)] ?: DEFAULT_THUMBNAIL
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaUrl = response.request.url
        val mangaType = mangaUrl.queryParameter("type")!!

        val selectorComplement = when (mangaType) {
            "main" -> "#volumes"
            "sbs" -> "#volumes div.volume header:contains(SBS)"
            else -> "#post > div.volume:contains(" + mangaUrl.queryParameter("title")!!.substringAfter(" - ") + ")"
        }

        val chapterListSelector = selectorComplement + (if (mangaType == "sbs") "" else " " + chapterListSelector())

        return response.asJsoup()
            .select(chapterListSelector)
            .map(::chapterFromElement)
            .reversed()
    }

    override fun chapterListSelector() = "div.capitulos li.volume-capitulo"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val mangaUrl = element.ownerDocument()!!.location().toHttpUrlOrNull()!!

        when (mangaUrl.queryParameter("type")!!) {
            "main" -> {
                name = element.select("span").first()!!.text()
                element.selectFirst("a.online")!!.attr("abs:href")
                    .substringBefore("?")
                    .let { setUrlWithoutDomain(it) }
            }
            "sbs" -> {
                name = element.select("div.volume-nome h2").first()!!.text()
                element.selectFirst("header p.extra a:contains(SBS)")!!.attr("abs:href")
                    .substringBefore("?")
                    .let { setUrlWithoutDomain(it) }
            }
            "special" -> {
                name = element.ownText()
                element.select("a.online").first()!!.attr("abs:href")
                    .substringBefore("?")
                    .let { setUrlWithoutDomain(it) }
            }
        }

        scanlator = this@OnePieceEx.name
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("script:containsData(paginasLista)").first()!!
            .data()
            .substringAfter("paginasLista = ")
            .substringBefore(";")
            .let { json.parseToJsonElement(it).jsonPrimitive.content }
            .let { json.parseToJsonElement(it).jsonObject.entries }
            .mapIndexed { i, entry ->
                Page(i, document.location(), "$baseUrl/${entry.value.jsonPrimitive.content}")
            }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", ACCEPT_IMAGE)
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    private fun bypassHttp103Intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.pathSegments[0] != "mangas") {
            return chain.proceed(request)
        }

        val bypasserUrl = "https://translate.google.com/translate".toHttpUrl().newBuilder()
            .addQueryParameter("pto", "op")
            .addQueryParameter("u", request.url.toString())
            .build()

        val bypasserRequest = request.newBuilder()
            .url(bypasserUrl)
            .build()

        val bypasserResponse = chain.proceed(bypasserRequest)
        val fixedBody = bypasserResponse.body.string()
            .replace("onepieceex-net.translate.goog", baseUrl.removePrefix("https://"))
            .toResponseBody(bypasserResponse.body.contentType())

        return bypasserResponse.newBuilder()
            .body(fixedBody)
            .request(request)
            .build()
    }

    companion object {
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9," +
            "image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        private const val ACCEPT_IMAGE = "image/webp,image/apng,image/*,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"

        private const val DEFAULT_THUMBNAIL = "https://onepieceex.net/mangareader/sbs/capa/preview/nao.jpg"
        private const val MAIN_SERIES_THUMBNAIL = "https://onepieceex.net/mangareader/sbs/capa/preview/Volume_1.jpg"
        private val THUMBNAIL_URL_MAP = mapOf(
            "OPEX - DENSETSU NO SEKAI" to "https://onepieceex.net/mangareader/especiais/501/00.jpg",
            "OPEX - ESPECIAIS" to "https://onepieceex.net/mangareader/especiais/27/00.jpg",
            "ONE PIECE - ESPECIAIS DE ONE PIECE" to "https://onepieceex.net/mangareader/especiais/5/002.png",
            "ONE PIECE - HISTÓRIAS DE CAPA" to "https://onepieceex.net/mangareader/mangas/428/00_c.jpg",
        )
    }
}
