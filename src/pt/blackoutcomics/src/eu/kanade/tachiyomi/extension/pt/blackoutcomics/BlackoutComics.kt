package eu.kanade.tachiyomi.extension.pt.blackoutcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class BlackoutComics : ParsedHttpSource() {

    override val name = "Blackout Comics"

    override val baseUrl = "https://blackoutcomics.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client by lazy {
        network.client.newBuilder()
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val request = response.request
                if (request.url.pathSegments.contains("login")) {
                    throw IOException("Faça o login na WebView para acessar o contéudo")
                }
                response
            }
            .rateLimitHost(baseUrl.toHttpUrl(), 2)
            .build()
    }

    override fun headersBuilder() =
        super.headersBuilder()
            .add("Referer", "$baseUrl/")
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .add("Accept-Language", "en-US,en;q=0.5")
            .add("X-Requested-With", randomString((1..20).random()))

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/ranking")

    override fun popularMangaSelector() = "section > div.container div > a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        thumbnail_url = element.selectFirst("img.custom-image:not(.hidden), img.img-comics")?.absUrl("src")
        title = element.selectFirst("p.image-name:not(.hidden), span.text-comic")!!.text()
    }

    override fun popularMangaNextPageSelector() = null

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/recentes")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = null

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val id = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/comics/$id"))
                .asObservableSuccess()
                .map(::searchMangaByIdParse)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response.use { it.asJsoup() })
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Using URLBuilder just to prevent issues with strange queries
        val url = "$baseUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = null

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val row = document.selectFirst("div.special-edition")!!
        thumbnail_url = row.selectFirst("img:last-child")?.absUrl("src")
        title = row.select("h2")
            .first { it.classNames().isEmpty() }!!
            .text()

        with(row.selectFirst("div.trailer-content:has(h3:containsOwn(Detalhes))")!!) {
            println(outerHtml())
            artist = getInfo("Artista")
            author = getInfo("Autor")
            genre = getInfo("Genêros")
            status = when (getInfo("Status")) {
                "Completo" -> SManga.COMPLETED
                "Em Lançamento" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }

            description = buildString {
                // Synopsis
                row.selectFirst("h3:containsOwn(Descrição) + p")?.ownText()?.also {
                    append("$it\n\n")
                }

                row.selectFirst("h2:contains($title) + p")?.ownText()?.also {
                    // Alternative title
                    append("Título alternativo: $it\n")
                }

                // Additional info
                listOf("Editora", "Lançamento", "Scans", "Tradução", "Cleaner", "Vizualizações")
                    .forEach { item ->
                        selectFirst("p:contains($item)")
                            ?.text()
                            ?.also { append("$it\n") }
                    }
            }
        }
    }

    private fun Element.getInfo(text: String) =
        selectFirst("p:contains($text)")?.run {
            selectFirst("b")?.text() ?: ownText()
        }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "section.relese > div.container > div.row h5:has(a)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("form + a")!!.run {
            setUrlWithoutDomain(attr("href"))
            name = text()
            chapter_number = name.substringAfter(" ").toFloatOrNull() ?: 1F
        }

        date_upload = element.selectFirst("form + a + span")?.text().orEmpty().toDate()
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div[class*=cap] canvas[height][width]").mapIndexed { index, item ->
            val attr = item.attributes()
                .firstOrNull { it.value.contains("/assets/obras", ignoreCase = true) }
                ?.key ?: throw Exception("Capitulo não pode ser obtido")

            Page(index, "", item.absUrl(attr))
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    // ============================= Utilities ==============================
    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(trim())?.time }
            .getOrNull() ?: 0L
    }

    private fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    companion object {
        const val PREFIX_SEARCH = "id:"

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        }
    }
}
