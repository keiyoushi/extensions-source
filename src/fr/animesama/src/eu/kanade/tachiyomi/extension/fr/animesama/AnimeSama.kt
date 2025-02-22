package eu.kanade.tachiyomi.extension.fr.animesama

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimeSama : ParsedHttpSource() {

    override val name = "AnimeSama"

    override val baseUrl = "https://anime-sama.fr"

    private val cdn = "$baseUrl/s2/scans/"

    override val lang = "fr"

    override val supportsLatest = false

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
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/catalogue/searchbar.php"
        val formBody = FormBody.Builder()
            .add("query", query)
            .build()

        return POST(url, headers, formBody)
    }

    override fun searchMangaSelector() = ".cardListAnime.Scans"
    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h1").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("img").attr("src")
        }
    }

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        description = document.select("#sousBlocMilieu > div h2:contains(Synopsis)+p").text()
        genre = document.select("#sousBlocMilieu > div h2:contains(Genres)+a").text()
        title = document.select("#titreOeuvre").text()
        thumbnail_url = document.select("#coverOeuvre").attr("src")
    }

    // Chapters
    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    fun String.containsMultipleTimes(search: String): Boolean {
        val regex = Regex(search)
        val matches = regex.findAll(this)
        val count = matches.count()
        return count > 1
    }

    private fun parseChapterFromResponse(response: Response, translation_name: String): List<SChapter> {
        val document = response.asJsoup()

        val chapterUrl = document.baseUri().toHttpUrl()
            .newBuilder()
            .query(null)
            .addPathSegment("episodes.js")
            .addQueryParameter("title", document.select("#titreOeuvre").text())
            .build()

        val requestToFetchChapters = GET(chapterUrl, headers)
        val javascriptFile = client.newCall(requestToFetchChapters).execute()
        val javascriptFileContent = javascriptFile.body.string()

        val parsedJavascriptFileToJson = javascriptFileContent
            .split(" ", ",")
            .filter { it.contains("eps") && !it.contains("drive.google.com") }
            .mapNotNull { it.replace("=", "").replace("eps", "").toIntOrNull() }
            .sorted()
            .asReversed()
            .toSet() // Remove duplicate episodes
        val parsedChapterList: MutableList<SChapter> = ArrayList()
        var chapterDelay = 0

        val scriptContent = document.select("script:containsData(resetListe\\(\\))").toString()
        if (scriptContent.containsMultipleTimes("resetListe()")) {
            val scriptCommandList = document.html().split(";")
            val createListRegex = Regex("""creerListe\((\d+,\s*\d+)\)""")
            val specialRegex = Regex("""newSP\((\d+(\.\d+)?|"(.*?)")\)""")
            scriptCommandList.forEach { command ->
                when {
                    createListRegex.find(command) != null -> {
                        val data = createListRegex.find(command)!!.groupValues[1].split(",")
                        val start = data[0].replace(" ", "").toInt()
                        val end = data[1].replace(" ", "").toInt()

                        for (i in start..end) {
                            parsedChapterList.add(
                                SChapter.create().apply {
                                    name = "Chapitre $i"
                                    setUrlWithoutDomain(chapterUrl.newBuilder().addQueryParameter("id", (parsedChapterList.size + 1).toString()).build().toString())
                                    scanlator = translation_name
                                },
                            )
                        }
                    }
                    specialRegex.find(command) != null -> {
                        val title = specialRegex.find(command)!!.groupValues[1]
                        parsedChapterList.add(
                            SChapter.create().apply {
                                name = "Chapitre $title"
                                setUrlWithoutDomain(chapterUrl.newBuilder().addQueryParameter("id", (parsedChapterList.size + 1).toString()).build().toString())
                                scanlator = translation_name
                            },
                        )
                        chapterDelay++
                    }
                    /* The site contains an as-yet unused command called "newSPE", and as I have no concrete examples of its use, I haven't implemented it yet. */
                }
            }
        }
        for (index in parsedChapterList.size..parsedJavascriptFileToJson.size - 1) {
            parsedChapterList.add(
                SChapter.create().apply {
                    name = "Chapitre " + (parsedChapterList.size + 1 - chapterDelay)
                    setUrlWithoutDomain(chapterUrl.newBuilder().addQueryParameter("id", (parsedChapterList.size + 1).toString()).build().toString())
                    scanlator = translation_name
                },
            )
        }
        return parsedChapterList
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url.toUrl().toHttpUrlOrNull()!!
        val document = response.asJsoup()
        val scriptContent = document.select("script:containsData(panneauScan(\"nom\", \"url\"))").toString()
        val splitedContent = scriptContent.split(";").toMutableList()
        // Remove exemple
        splitedContent.removeAt(0)

        val parsedChapterList: MutableList<SChapter> = mutableListOf()

        splitedContent.forEach { line ->
            val pattern = """panneauScan\("(.+?)", "(.+?)"\)""".toRegex()
            val matchResult = pattern.find(line)
            if (matchResult != null) {
                val (scanTitle, scanUrl) = matchResult.destructured
                if (!scanUrl.contains("va")) {
                    val scanlatorGroup = scanTitle.replace(Regex("""(Scans|\(|\))"""), "").trim()
                    val fetchExistentSubMangas = GET(
                        url.newBuilder()
                            .addPathSegments(
                                scanUrl,
                            ).build(),
                        headers,
                    )
                    val res = client.newCall(fetchExistentSubMangas).execute()
                    parsedChapterList.addAll(parseChapterFromResponse(res, scanlatorGroup))
                }
            }
        }

        parsedChapterList.sortBy { chapter -> ("$baseUrl${chapter.url}").toHttpUrl().queryParameter("id")?.toIntOrNull() }

        return parsedChapterList.asReversed()
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val url = document.baseUri().toHttpUrl()

        val title = url.queryParameter("title")
        val chapter = url.queryParameter("id")

        val allChapters = document.body().toString().split("var")

        val chapterImageListString = allChapters.firstOrNull { it.contains("eps$chapter=") }
            ?: return emptyList()

        val chapterImageListParsed = chapterImageListString
            .substringAfter("[")
            .substringBefore("]")
            .split(",")

        val image_list = mutableListOf<Page>()

        for (index in 1 until chapterImageListParsed.size) {
            image_list.add(
                Page(index, imageUrl = "$cdn$title/$chapter/$index.jpg"),
            )
        }

        return image_list
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .add("Referer", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }
}
