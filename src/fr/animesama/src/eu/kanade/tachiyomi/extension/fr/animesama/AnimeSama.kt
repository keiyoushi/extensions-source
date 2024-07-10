package eu.kanade.tachiyomi.extension.fr.animesama

import android.net.Uri
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class AnimeSama : ParsedHttpSource() {

    override val name = "AnimeSama"

    override val baseUrl = "https://anime-sama.fr"

    val cdn = "https://anime-sama.fr/s2/scans/"

    override val lang = "fr"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr-FR")

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/catalogue/", headers)
    }

    override fun popularMangaSelector() = ".cardListAnime.Scans"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h1").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("img").attr("src")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/catalogue/", headers)
    }

    override fun latestUpdatesSelector() = ".cardListAnime.Scans"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h1").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("img").attr("src")
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/template-php/defaut/fetch.php").buildUpon()
            .appendQueryParameter("query", query)
        val mediaType = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()
        return POST(uri.toString(), headers, "query=$query".toRequestBody(mediaType))
    }
    override fun searchMangaSelector() = "a[href*='catalogue']"
    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h3").text()
            setUrlWithoutDomain(element.attr("href"))
            thumbnail_url = element.select("img").attr("src")
        }
    }

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        var boolSyn = false
        var boolGenre = false

        document.select("#sousBlocMilieu > div").first()!!.children().forEach { element ->
            if (boolSyn) {
                boolSyn = false
                description = element.text()
            }

            if (element.text() == "Synopsis") {
                boolSyn = true
            }

            if (boolGenre) {
                boolGenre = false
                genre = element.text()
            }

            if (element.text() == "Genres") {
                boolGenre = true
            }
        }
        title = document.select("#titreOeuvre").text()
        thumbnail_url = document.select("#coverOeuvre").attr("src")
    }

    // Chapters
    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    fun String.containsMultipleTimes(search: String): Boolean {
        if (this.contains(search)) {
            var temp = this.split(search)[1]
            return temp.contains(search)
        }
        return false
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        val goodRequest = GET(document.baseUri() + "/scan/vf")
        val goodResponse = client.newCall(goodRequest).execute()
        document = goodResponse.asJsoup()

        Log.v("AnimeSama", document.baseUri())
        val javascriptUrl = "episodes.js?title=" + document.select("#titreOeuvre").text()
        Log.v("AnimeSama", javascriptUrl)
        val newHeaders = headersBuilder()
            .add("Accept-Version", "v1")
            .build()

        val request = GET(document.baseUri() + "/" + javascriptUrl, newHeaders)
        val responsejs = client.newCall(request).execute()
        var jsonDataString = responsejs.body.string()

        val toJson = jsonDataString
            .split(" ", ",")
            .filter { it.contains("eps") && !it.contains("drive.google.com") }
            .mapNotNull { it.replace("=", "").replace("eps", "").toIntOrNull() }
            .sorted()
            .map { chapter ->
            }.asReversed()
        var chapList: MutableList<SChapter> = ArrayList()
        var retard = 0

        Log.v("AnimeSama", document.html())
        if (document.html().containsMultipleTimes("resetListe()")) {
            var list = document.html().split("resetListe();").toMutableList()
            list.removeAt(0)
            list.removeAt(0)
            list[0] = list[0].split("finirList")[0]
            list = list[0].split(");").toMutableList()
            Log.v("AnimeSama", list.toString())
            list.forEach { command ->
                when {
                    command.contains("creerListe(") -> {
                        var clean = command.replace("creerListe(", "").trimIndent().split(",")
                        var start = clean[0].replace(" ", "").toInt()
                        var end = clean[1].replace(" ", "").toInt()

                        for (i in start..end) {
                            Log.v("AnimeSama", "" + (chapList.size + 1) + " - " + i)
                            chapList.add(
                                SChapter.create().apply {
                                    name = "Chapitre " + (i)
                                    setUrlWithoutDomain(document.baseUri() + "/" + javascriptUrl + "&id=${chapList.size + 1}")
                                },
                            )
                        }
                    }
                    command.contains("newSP(") -> {
                        var clean = command.replace("newSP(", "").trimIndent()
                        Log.v("AnimeSama", "" + (chapList.size + 1) + " - " + clean)
                        chapList.add(
                            SChapter.create().apply {
                                name = "Chapitre " + (clean)
                                setUrlWithoutDomain(document.baseUri() + "/" + javascriptUrl + "&id=${chapList.size + 1}")
                            },
                        )
                        retard++
                    }
                }
            }
        }
        for (index in chapList.size..toJson.size - 1) {
            Log.v("AnimeSama", "" + (chapList.size + 1) + " - " + (chapList.size + 1 - retard))
            chapList.add(
                SChapter.create().apply {
                    name = "Chapitre " + (chapList.size + 1 - retard)
                    setUrlWithoutDomain(document.baseUri() + "/" + javascriptUrl + "&id=${chapList.size + 1}")
                },
            )
        }
        return chapList.asReversed()
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        Log.v("AnimeSama", document.baseUri())
        val url = URL(document.baseUri())
        val query = url.query
        val parameters = query.split("&").associate {
            val (key, value) = it.split("=")
            key to URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
        }

        val title = parameters["title"]
        val chapter = parameters["id"]
        val javascriptResponse = checkJavascript(document.baseUri())
        val jsonDataString = javascriptResponse.body.string()

        val allEpisodes = jsonDataString.split("var")

        val theEpisode = allEpisodes.firstOrNull { it.contains("eps$chapter=") }
            ?: return emptyList()

        val final_list = theEpisode
            .substringAfter("[")
            .substringBefore("]")
            .split(",")

        val image_list = mutableListOf<Page>()

        for (index in 1..final_list.size - 1) {
            image_list.add(
                Page(index, "", "$cdn$title/$chapter/$index.jpg"),
            )
        }

        return image_list
    }

    private fun checkJavascript(url: String): Response {
        val request = GET(url, headers)

        return client.newCall(request).execute()
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .add("Referer", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }
}
