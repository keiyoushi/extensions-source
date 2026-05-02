package eu.kanade.tachiyomi.extension.fr.animesama

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
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
import rx.Observable
import java.io.IOException

class AnimeSama : HttpSource() {

    override val name = "AnimeSama"

    override val baseUrl = "https://anime-sama.to"

    override val lang = "fr"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr-FR")

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/catalogue".toHttpUrl().newBuilder()
            .addQueryParameter("type[0]", "Scans")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#list_catalog > div").map {
            SManga.create().apply {
                title = it.select(".card-title").text()
                setUrlWithoutDomain(it.select("a").attr("href"))
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("#list_pagination > a.bg-sky-900 + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Latest =========================
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#containerAjoutsScans > div").map {
            SManga.create().apply {
                title = it.select(".card-title").text()
                setUrlWithoutDomain(it.select("a").attr("href").removeSuffix("scan/vf/"))
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPageResult = document.selectFirst("#list_pagination > a.bg-sky-900 + a") != null
        return MangasPage(mangas, hasNextPageResult)
    }

    // ========================= Search =========================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val url = query.toHttpUrlOrNull()
        if (url != null && url.host.contains("anime-sama") && url.pathSegments.contains("catalogue")) {
            val path = url.encodedPath.removeSuffix("/").substringBefore("/scan")
            val mangaUrl = url.newBuilder().encodedPath("$path/").build()
            return client.newCall(GET(mangaUrl, headers))
                .asObservableSuccess()
                .map { response ->
                    val manga = detailsParse(response)
                    MangasPage(listOfNotNull(manga), false)
                }
        }

        return super.fetchSearchManga(page, query, filters)
    }

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

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    private fun detailsParse(response: Response): SManga? {
        val document = response.asJsoup()
        val scriptContent = document.select("script:containsData(panneauScan(\"nom\", \"url\"))").toString()
        val splitedContent = scriptContent.split(";").toMutableList()
        if (splitedContent.isNotEmpty()) {
            splitedContent.removeAt(0)
        }

        val numberOfScans = splitedContent.count { line ->
            val matchResult = PANNEAU_SCAN_REGEX.find(line)
            matchResult != null && !matchResult.groupValues[2].contains("va")
        }

        if (numberOfScans == 0) return null

        return SManga.create().apply {
            description = document.select("#sousBlocMiddle > div h2:contains(Synopsis)+p").text()
            genre = document.select("#sousBlocMiddle > div h2:contains(Genres)+a").text()
            title = document.select("#titreOeuvre").text()
            thumbnail_url = document.selectFirst("#coverOeuvre")?.absUrl("src")
            setUrlWithoutDomain(document.baseUri())
        }
    }

    // ========================= Filters =========================
    private var genreList = listOf<Pair<String, String>>()
    private var fetchFilterAttempts = 0

    private suspend fun fetchFilters() {
        if (fetchFilterAttempts < 3 && genreList.isEmpty()) {
            try {
                val response = client.newCall(GET("$baseUrl/catalogue", headers)).await().asJsoup()
                genreList = response.select("#list_genres #genreList label").mapNotNull { labelElement ->
                    val input = labelElement.selectFirst("input[name=genre[]]") ?: return@mapNotNull null
                    val labelText = labelElement.selectFirst("span")?.text() ?: return@mapNotNull null
                    val value = input.attr("value")
                    labelText to value
                }
            } catch (e: Exception) {
                // Ignore errors
            }
            fetchFilterAttempts++
        }
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        GlobalScope.launch { fetchFilters() }

        if (genreList.isNotEmpty()) {
            filters.add(GenreFilter(genreList))
        }
        if (filters.isEmpty()) {
            filters.add(0, Filter.Header("Press 'reset' to load more filters"))
        }

        return FilterList(filters)
    }

    // ========================= Details =========================
    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        description = document.select("#sousBlocMiddle > div h2:contains(Synopsis)+p").text()
        genre = document.select("#sousBlocMiddle > div h2:contains(Genres)+a").text()
        title = document.select("#titreOeuvre").text()
        thumbnail_url = document.selectFirst("#coverOeuvre")?.absUrl("src")
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    // ========================= Chapters =========================
    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url.toString().toHttpUrlOrNull()!!
        val document = response.asJsoup()
        val scriptContent = document.select("script:containsData(panneauScan(\"nom\", \"url\"))").toString()
        val splitedContent = scriptContent.split(";").toMutableList()
        if (splitedContent.isNotEmpty()) {
            splitedContent.removeAt(0)
        }

        val parsedChapterList = mutableListOf<SChapter>()
        splitedContent.forEach { line ->
            val matchResult = PANNEAU_SCAN_REGEX.find(line)
            if (matchResult != null) {
                val (scanTitle, scanUrl) = matchResult.destructured
                if (!scanUrl.contains("va")) {
                    val scanlatorGroup = scanTitle.replace(SCANS_REGEX, "").trim()
                    val fetchExistentSubMangas = GET(
                        url.newBuilder()
                            .addPathSegments(scanUrl)
                            .build(),
                        headers,
                    )
                    try {
                        val res = client.newCall(fetchExistentSubMangas).execute()
                        parsedChapterList.addAll(parseChapterFromResponse(res, scanlatorGroup))
                    } catch (e: IOException) {
                        // Ignore
                    }
                }
            }
        }

        parsedChapterList.sortBy { chapter -> "$baseUrl${chapter.url}".toHttpUrl().queryParameter("id")?.toIntOrNull() }

        return parsedChapterList.asReversed()
    }

    private fun parseChapterFromResponse(response: Response, translationName: String): List<SChapter> {
        val document = response.asJsoup()
        val title = document.select("#titreOeuvre").textNodes().firstOrNull()?.wholeText?.trim() ?: ""
        val chapterUrl = "$baseUrl/s2/scans/get_nb_chap_et_img.php".toHttpUrl()
            .newBuilder()
            .addQueryParameter("oeuvre", title)
            .build()

        val apiNbChapImgResponse = client.newCall(GET(chapterUrl, headers)).execute()
        val apiNbChapImgJson = Json.decodeFromString<Map<String, Int>>(apiNbChapImgResponse.body.string())

        val parsedChapterList = mutableListOf<SChapter>()
        var chapterDelay = 0

        val html = document.html()
        if (html.contains("resetListe()")) {
            val scriptCommandList = html.split(";")
            scriptCommandList.forEach { command ->
                when {
                    CREER_LISTE_REGEX.find(command) != null -> {
                        val data = CREER_LISTE_REGEX.find(command)!!.groupValues[1].split(",")
                        val start = data[0].trim().toInt()
                        val end = data[1].trim().toInt()

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

                    NEW_SP_REGEX.find(command) != null -> {
                        val chapterName = NEW_SP_REGEX.find(command)!!.groupValues[1]
                        parsedChapterList.add(
                            SChapter.create().apply {
                                name = "Chapitre $chapterName"
                                setUrlWithoutDomain(chapterUrl.newBuilder().addQueryParameter("id", (parsedChapterList.size + 1).toString()).addQueryParameter("title", title).build().toString())
                                scanlator = translationName
                            },
                        )
                        chapterDelay++
                    }
                }
            }
        }

        (parsedChapterList.size until apiNbChapImgJson.size).forEach { _ ->
            parsedChapterList.add(
                SChapter.create().apply {
                    name = "Chapitre ${parsedChapterList.size + 1 - chapterDelay}"
                    setUrlWithoutDomain(chapterUrl.newBuilder().addQueryParameter("id", (parsedChapterList.size + 1).toString()).addQueryParameter("title", title).build().toString())
                    scanlator = translationName
                },
            )
        }
        return parsedChapterList
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ========================= Pages =========================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val url = document.baseUri().toHttpUrl()

        val title = url.queryParameter("oeuvre")
        val chapter = url.queryParameter("id")

        val chapterUrl = "$baseUrl/s2/scans/get_nb_chap_et_img.php".toHttpUrl()
            .newBuilder()
            .addQueryParameter("oeuvre", title)
            .build()

        val apiNbChapImgResponse = client.newCall(GET(chapterUrl, headers)).execute()
        val apiNbChapImgJson = Json.decodeFromString<Map<String, Int>>(apiNbChapImgResponse.body.string())
        val imageCount = apiNbChapImgJson[chapter] ?: 0

        return (1..imageCount).map { index ->
            Page(index, imageUrl = "$baseUrl/s2/scans/$title/$chapter/$index.jpg")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .add("Referer", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    companion object {
        private val PANNEAU_SCAN_REGEX = """panneauScan\("(.+?)", "(.+?)"\)""".toRegex()
        private val CREER_LISTE_REGEX = """creerListe\((\d+,\s*\d+)\)""".toRegex()
        private val NEW_SP_REGEX = """newSP\((\d+(\.\d+)?|"(.*?)")\)""".toRegex()
        private val SCANS_REGEX = """(Scans|\(|\))""".toRegex()
    }
}
