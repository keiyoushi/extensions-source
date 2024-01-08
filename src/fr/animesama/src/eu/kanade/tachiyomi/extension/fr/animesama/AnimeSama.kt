package eu.kanade.tachiyomi.extension.fr.animesama

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class AnimeSama : ParsedHttpSource() {

    override val name = "AnimeSama"

    override val baseUrl = "https://anime-sama.fr"

    val cdn_url = "https://cdn.statically.io/gh/Anime-Sama/IMG/img/animes/animes%20icones%20carr%C3%A9/"

    override val lang = "fr"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json by injectLazy()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr-FR")

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/scan", headers)
    }

    override fun popularMangaSelector() = "figure.figure"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("figcaption").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("a > img").attr("src")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "div.container-fluid:nth-child(15) > div:nth-child(1) figure"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("figcaption").text().replace("\\nScan\\n", "")
            setUrlWithoutDomain(cdn_url + title.replace(" ", "-").trim() + "carre.jpg")
            thumbnail_url = element.select("img").attr("src")
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/search/search.php").buildUpon()
            .appendQueryParameter("terme", query + " [SCANS]")
            .appendQueryParameter("s", "Search")
        return GET(uri.toString(), headers)
    }
    override fun searchMangaSelector() = "div.media-body"
    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h5").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url =
                cdn_url + title.replace(
                " [SCANS]",
                "",
            ).replace(" ", "-").trim() + "carre.jpg"
        }
    }

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("div.carousel-item:nth-child(1) > div:nth-child(2) > h5:nth-child(1)").text()
        description = document.select("div.carousel-item:nth-child(2) > div:nth-child(2) > p:nth-child(1)").text()
        thumbnail_url = document.select("div.carousel-item:nth-child(1) > img:nth-child(1)").attr("src")
        genre = document.select("div.carousel-item:nth-child(2) > div:nth-child(2) > p:nth-child(2)").text().replace("Genres : ", "")
    }

    // Chapters
    override fun chapterListSelector() = throw Exception("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val javascriptUrl = document.select("body > script:nth-child(3)").attr("abs:src")

        val newHeaders = headersBuilder()
            .add("Accept-Version", "v1")
            .build()

        val request = GET(javascriptUrl, newHeaders)
        val responsejs = client.newCall(request).execute()
        val jsonDataString = responsejs.body.string()

        return jsonDataString
            .split(" ", ",")
            .filter { it.contains("eps") && !it.contains("drive.google.com") }
            .mapNotNull { it.replace("=", "").replace("eps", "").toIntOrNull() }
            .sorted()
            .map { chapter ->
                SChapter.create().apply {
                    name = "Chapitre $chapter"
                    setUrlWithoutDomain(javascriptUrl + "/$chapter")
                }
            }
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val url = document.baseUri().split("/")
        val javascriptUrlFinal = url.subList(0, url.size - 1).joinToString("/")
        val javascriptResponse = checkJavascript(javascriptUrlFinal)
        val jsonDataString = javascriptResponse.body.string()

        val episode = url[url.size - 1]
        val allEpisodes = jsonDataString.split("var")

        val theEpisode = allEpisodes.firstOrNull { it.contains("eps$episode") }
            ?: return emptyList()

        val final_list = theEpisode
            .substringAfter("[")
            .substringBefore("]")

        return final_list
            .substring(0, final_list.lastIndexOf(",")).replace("""'""".toRegex(), "\"")
            .let { json.decodeFromString<List<String>>("[$it]") }
            .mapIndexed { i, imageUrl -> Page(i, "", imageUrl) }
    }

    private fun checkJavascript(url: String): Response {
        val request = GET(url, headers)

        return client.newCall(request).execute()
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .add("Referer", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }
}
