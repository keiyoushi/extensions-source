package eu.kanade.tachiyomi.extension.fr.scanmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class ScanManga : ParsedHttpSource() {

    override val name = "Scan-Manga"

    override val baseUrl = "https://m.scan-manga.com"

    override val lang = "fr"

    override val supportsLatest = true

    private val desktopBaseUrl = "https://www.scan-manga.com" // URL desktop pour la recherche

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor { chain ->
            val originalCookies = chain.request().header("Cookie") ?: ""
            val newReq = chain
                .request()
                .newBuilder()
                .header("Cookie", "$originalCookies; _ga=GA1.2.${shuffle("123456789")}.${System.currentTimeMillis() / 1000}")
                .build()
            chain.proceed(newReq)
        }.build()

    private val json: Json by injectLazy()
    private fun shuffle(s: String): String {
        val chars = s.toMutableList()
        chars.shuffle()
        return chars.joinToString("")
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr-FR")
        .set(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 16; LM-X420) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.7204.158 Mobile Safari/537.36",
        )

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/TOP-Manga-Webtoon-36.html", headers)
    }

    override fun popularMangaSelector() = "div.top"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        val titleElement = element.selectFirst("a.atop")
        val imgElement = element.selectFirst("a > img")

        manga.setUrlWithoutDomain(titleElement?.attr("href")?.removePrefix(baseUrl) ?: "")
        manga.title = titleElement?.text()?.trim() ?: "Titre inconnu"
        manga.thumbnail_url = imgElement?.attr("data-original") ?: imgElement?.attr("data-original")
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "#content_news .listing"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("a.nom_manga").text()
            setUrlWithoutDomain(element.select("a.nom_manga").attr("href"))
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = desktopBaseUrl.toHttpUrl().newBuilder()
            .addPathSegments("scanlation/liste_series.html")
            .addQueryParameter("q", query)
            .build()
            .toString()

        return GET(
            url,
            headersBuilder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0 Safari/537.36")
                .add("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
                .add("Referer", "$desktopBaseUrl/")
                .build(),
        )
    }

    override fun searchMangaSelector() = "div.texte_manga.book_close, div.texte_manga.book_stop"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val link = element.selectFirst("a.texte_manga")!!

        val path = link.attr("href")
        // Construction correcte de l'URL vers la version mobile
        manga.setUrlWithoutDomain(path)
        manga.url = if (path.startsWith("/")) "https://m.scan-manga.com$path" else path

        manga.title = link.text().trim()
        return manga
    }

    override fun searchMangaNextPageSelector(): String? = null

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        manga.title = document.select("h1.main_title[itemprop=name]").text()
        manga.author = document.select("div[itemprop=author]").text()
        manga.description = document.select("div.titres_desc[itemprop=description]").text()
        manga.genre = document.select("div.titres_souspart span[itemprop=genre]").joinToString { it.text() }

        val statutText = document.select("div.titres_souspart").firstOrNull { it.text().contains("Statut") }?.ownText()
        manga.status = when {
            statutText?.contains("En cours", ignoreCase = true) == true -> SManga.ONGOING
            statutText?.contains("Terminé", ignoreCase = true) == true -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        manga.thumbnail_url = document.select("div.full_img_serie img[itemprop=image]").absUrl("src")

        return manga
    }

    // Chapters
    override fun chapterListSelector() = "div.chapt_m"

    override fun chapterFromElement(element: Element): SChapter {
        val linkEl = element.selectFirst("td.publimg span.i a")!!
        val titleEl = element.selectFirst("td.publititle")

        val chapterName = linkEl.text()
        val extraTitle = titleEl?.text()?.orEmpty()

        return SChapter.create().apply {
            name = if (extraTitle.isNotEmpty()) "$chapterName - $extraTitle" else chapterName
            setUrlWithoutDomain(linkEl.absUrl("href"))
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val docString = document.toString()

        var lelUrl = Regex("""['"](http.*?scanmanga.eu.*)['"]""").find(docString)?.groupValues?.get(1)
        if (lelUrl == null) {
            lelUrl = Regex("""['"](http.*?le[il].scan-manga.com.*)['"]""").find(docString)?.groupValues?.get(1)
        }

        return Regex("""["'](.*?zoneID.*?pageID.*?siteID.*?)["']""").findAll(docString).toList().mapIndexed { i, pageParam ->
            Page(i, document.location(), lelUrl + pageParam.groupValues[1])
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .add("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }
}
