package eu.kanade.tachiyomi.extension.fr.animesama

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class AnimeSama : ParsedHttpSource() {

    override val name = "AnimeSama"

    override val baseUrl = "https://anime-sama.pw"

    private val cdn = "$baseUrl/s2/scans/"

    override val lang = "fr"

    override val supportsLatest = true
    private val interceptor = AnimeSamaInterceptor(network.cloudflareClient, baseUrl, headers)
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(interceptor)
        .build()

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
        genreList = document.select("#list_genres #genreList label").mapNotNull { labelElement ->
            val input = labelElement.selectFirst("input[name=genre[]]") ?: return@mapNotNull null
            val labelText = labelElement.selectFirst("span")?.text() ?: return@mapNotNull null
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

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select(".card-title").text()
        setUrlWithoutDomain(element.select("a").attr("href"))
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector(): String = "#list_pagination > a.bg-sky-900 + a"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "#containerAjoutsScans > div"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select(".card-title").text()
        setUrlWithoutDomain(element.select("a").attr("href").removeSuffix("scan/vf/"))
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
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

    private fun detailsParse(response: Response): SManga? {
        val document = response.asJsoup()

        val scriptContent = document.select("script:containsData(panneauScan(\"nom\", \"url\"))").toString()
        val splitedContent = scriptContent.split(";").toMutableList()
        // Remove exemple
        splitedContent.removeAt(0)

        val pattern = """panneauScan\("(.+?)", "(.+?)"\)""".toRegex()
        val numberOfScans = splitedContent.count { line ->
            val matchResult = pattern.find(line)
            matchResult != null && !matchResult.groupValues[2].contains("va")
        }

        if (numberOfScans == 0) return null

        val manga: SManga = SManga.create()
        manga.description = document.select("#sousBlocMiddle > div h2:contains(Synopsis)+p").text()
        manga.genre = document.select("#sousBlocMiddle > div h2:contains(Genres)+a").text()
        manga.title = document.select("#titreOeuvre").text()
        manga.thumbnail_url = document.selectFirst("#coverOeuvre")?.absUrl("src")
        manga.setUrlWithoutDomain(document.baseUri())
        return manga
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith("ID:")) {
        val id = query.substringAfterLast("ID:")

        val mangaUrl = "$baseUrl/catalogue".toHttpUrl()
            .newBuilder()
            .addPathSegment(id)
            .build()
        val requestToCheckManga = GET(mangaUrl, headers)
        client.newCall(requestToCheckManga).asObservableSuccess().map {
            MangasPage(
                listOfNotNull(detailsParse(it)),
                false,
            )
        }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        description = document.select("#sousBlocMiddle > div h2:contains(Synopsis)+p").text()
        genre = document.select("#sousBlocMiddle > div h2:contains(Genres)+a").text()
        title = document.select("#titreOeuvre").text()
        thumbnail_url = document.selectFirst("#coverOeuvre")?.absUrl("src")
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

        val title = document.select("#titreOeuvre").textNodes()[0]?.wholeText
        val chapterUrl = "$baseUrl/s2/scans/get_nb_chap_et_img.php".toHttpUrl()
            .newBuilder()
            .addQueryParameter("oeuvre", title)
            .build()
        val requestToFetchChapters = GET(chapterUrl, headers)

        val apiNbChapImgResponse = client.newCall(requestToFetchChapters).execute()
        val apiNbChapImgJson = Json.decodeFromString<Map<String, Int>>(apiNbChapImgResponse.body.string())

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
                                    setUrlWithoutDomain(chapterUrl.newBuilder().addQueryParameter("id", (parsedChapterList.size + 1).toString()).addQueryParameter("title", title).build().toString())
                                    scanlator = translationName
                                },
                            )
                        }
                    }

                    specialRegex.find(command) != null -> {
                        val chapterTitle = specialRegex.find(command)!!.groupValues[1]
                        parsedChapterList.add(
                            SChapter.create().apply {
                                name = "Chapitre $chapterTitle"
                                setUrlWithoutDomain(chapterUrl.newBuilder().addQueryParameter("id", (parsedChapterList.size + 1).toString()).addQueryParameter("title", title).build().toString())
                                scanlator = translationName
                            },
                        )
                        chapterDelay++
                    }
                    /* The site contains an as-yet unused command called "newSPE", and as I have no concrete examples of its use, I haven't implemented it yet. */
                }
            }
        }
        (parsedChapterList.size until apiNbChapImgJson.size).forEach { index ->
            parsedChapterList.add(
                SChapter.create().apply {
                    name = "Chapitre " + (parsedChapterList.size + 1 - chapterDelay)
                    setUrlWithoutDomain(chapterUrl.newBuilder().addQueryParameter("id", (parsedChapterList.size + 1).toString()).addQueryParameter("title", title).build().toString())
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
        val pattern = """panneauScan\("(.+?)", "(.+?)"\)""".toRegex()
        val scanPattern = Regex("""(Scans|\(|\))""")
        splitedContent.forEach { line ->
            val matchResult = pattern.find(line)
            if (matchResult != null) {
                val (scanTitle, scanUrl) = matchResult.destructured
                if (!scanUrl.contains("va")) {
                    val scanlatorGroup = scanTitle.replace(scanPattern, "").trim()
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

        parsedChapterList.sortBy { chapter -> "$baseUrl${chapter.url}".toHttpUrl().queryParameter("id")?.toIntOrNull() }

        return parsedChapterList.asReversed()
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val url = document.baseUri().toHttpUrl()

        val title = url.queryParameter("oeuvre")
        val chapter = url.queryParameter("id")

        val chapterUrl = "$baseUrl/s2/scans/get_nb_chap_et_img.php".toHttpUrl()
            .newBuilder()
            .addQueryParameter("oeuvre", title)
            .build()
        val requestToFetchChapters = GET(chapterUrl, headers)

        val apiNbChapImgResponse = client.newCall(requestToFetchChapters).execute()
        val apiNbChapImgJson = Json.decodeFromString<Map<String, Int>>(apiNbChapImgResponse.body.string())
        val imageCount = apiNbChapImgJson[chapter] ?: 0

        val imageList = (1..imageCount).map { index ->
            Page(index, imageUrl = "$cdn$title/$chapter/$index.jpg")
        }.toMutableList()

        return imageList
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .add("Referer", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    fun convertUrlToLatestDomain(url: String): String = "${interceptor.getBaseUrl()!!}$url"

    override fun getMangaUrl(manga: SManga): String = convertUrlToLatestDomain(manga.url)

    override fun getChapterUrl(chapter: SChapter): String = convertUrlToLatestDomain(chapter.url)
}
