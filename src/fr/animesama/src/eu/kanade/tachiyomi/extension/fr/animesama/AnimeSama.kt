package eu.kanade.tachiyomi.extension.fr.animesama

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr-FR")

    // filters
    private var genreList = listOf<Pair<String, String>>()
    private var fetchFilterAttempts = 0

    private suspend fun fetchFilters() {
        if (fetchFilterAttempts < 3 && (genreList.isEmpty())) {
            try {
                val response = client.newCall(filtersRequest()).await().asJsoup()
                parseFilters(response)
            } catch (e: Exception) {
                Log.e("$name: Filters", e.stackTraceToString())
            }
            fetchFilterAttempts++
        }
    }

    private fun filtersRequest() = GET("$baseUrl/catalogue", headers)

    private fun parseFilters(document: Document) {
        genreList = document.select("#list_genres label").mapNotNull { labelElement ->
            val input = labelElement.selectFirst("input[name=genre[]]") ?: return@mapNotNull null
            val labelText = labelElement.ownText()
            val value = input.attr("value")
            labelText to value
        }
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        launchIO { fetchFilters() }

        if (genreList.isNotEmpty()) {
            filters.add(GenreFilter(genreList))
        }
        if (filters.size < 1) {
            filters.add(0, Filter.Header("Press 'reset' to load more filters"))
        }

        return FilterList(filters)
    }

    private fun launchIO(block: suspend () -> Unit) = GlobalScope.launch { block() }

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/catalogue".toHttpUrl().newBuilder()
            .addQueryParameter("type[0]", "Scans")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "#list_catalog > div"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h1").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("img").attr("src")
        }
    }

    override fun popularMangaNextPageSelector(): String = "#list_pagination > a.bg-sky-900 + a"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "#containerAjoutsScans > div"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h1").text()
            setUrlWithoutDomain(element.select("a").attr("href").removeSuffix("scan/vf/"))
            thumbnail_url = element.select("img").attr("src")
        }
    }

    override fun latestUpdatesNextPageSelector() = "#list_pagination > a.bg-sky-900 + a"

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/catalogue".toHttpUrl().newBuilder()
            .addQueryParameter("type[0]", "Scans")
            .addQueryParameter("search", query)
            .addQueryParameter("page", page.toString())
            .apply {
                filters.filterIsInstance<UrlPartFilter>().forEach {
                    it.addUrlParameter(this)
                }
            }
            .build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        description = document.select("#sousBlocMiddle > div h2:contains(Synopsis)+p").text()
        genre = document.select("#sousBlocMiddle > div h2:contains(Genres)+a").text()
        title = document.select("#titreOeuvre").text()
        thumbnail_url = document.select("#coverOeuvre").attr("src")
    }

    // Chapters
    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    private fun String.containsMultipleTimes(search: String): Boolean {
        val regex = Regex(search)
        val matches = regex.findAll(this)
        val count = matches.count()
        return count > 1
    }

    private fun parseChapterFromResponse(response: Response, translationName: String): List<SChapter> {
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
            .let { Regex("""eps(\d+)""").findAll(it) }
            .map { it.groupValues[1].toInt() }
            .distinct() // Remove duplicate episodes
            .sortedDescending().toList()
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
                                    scanlator = translationName
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
                                scanlator = translationName
                            },
                        )
                        chapterDelay++
                    }
                    /* The site contains an as-yet unused command called "newSPE", and as I have no concrete examples of its use, I haven't implemented it yet. */
                }
            }
        }
        for (index in parsedChapterList.size until parsedJavascriptFileToJson.size) {
            parsedChapterList.add(
                SChapter.create().apply {
                    name = "Chapitre " + (parsedChapterList.size + 1 - chapterDelay)
                    setUrlWithoutDomain(chapterUrl.newBuilder().addQueryParameter("id", (parsedChapterList.size + 1).toString()).build().toString())
                    scanlator = translationName
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

        val documentString = document.body().toString()

        val allChapters: Map<Int, Int> = Regex("""eps(\d+)\s*(?:=\s*\[(.*?)]|\.length\s*=\s*(\d+))""")
            .findAll(documentString)
            .associate { match ->
                val episode = match.groupValues[1].toInt()
                val arrayContent = match.groupValues[2]
                val explicitLength = match.groupValues[3]

                val length = when {
                    explicitLength.isNotEmpty() -> explicitLength.toInt()
                    arrayContent.isNotEmpty() -> arrayContent.split(Regex(",\\s*")).count { it.isNotBlank() }
                    else -> 0
                }

                episode to length
            }

        val chapterSize = allChapters[chapter?.toInt()] ?: 1

        val imageList = mutableListOf<Page>()
        for (index in 1 until chapterSize + 1) {
            imageList.add(
                Page(index, imageUrl = "$cdn$title/$chapter/$index.jpg"),
            )
        }

        return imageList
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .add("Referer", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }
}
