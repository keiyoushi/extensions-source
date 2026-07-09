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
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable

@Source
abstract class AnimeSama : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr-FR")

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/catalogue".toHttpUrl().newBuilder()
            .addQueryParameter("type[]", "Scans")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangasPage(response, "div#list_catalog > div")

    // ========================= Latest =========================
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangasPage(response, "div#containerAjoutsScans > div", "scan/vf/")

    // ========================= Search =========================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val url = query.toHttpUrlOrNull()
        if (url != null && url.host == baseUrl.toHttpUrl().host && url.pathSegments.contains("catalogue")) {
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
            .addQueryParameter("type[]", "Scans")
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
        val scanPanelContent = getScanPanelContent(document)

        val numberOfScans = scanPanelContent.count { line ->
            val matchResult = SCAN_PANEL_REGEX.find(line)
            matchResult != null && !matchResult.groupValues[2].contains("va")
        }

        if (numberOfScans == 0) return null

        return SManga.create().apply {
            populateMangaDetails(document)
            setUrlWithoutDomain(document.baseUri())
        }
    }

    // ========================= Details =========================
    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        populateMangaDetails(response.asJsoup())
    }

    private fun SManga.populateMangaDetails(document: Document) {
        description = document.selectFirst("p#synopsisText")?.text()

        genre = document.select("span.genre-pill")
            .takeIf { it.isNotEmpty() }
            ?.joinToString { it.text() }

        title = document.selectFirst("div.my-2 h1")!!.text()
        thumbnail_url = document.selectFirst("img#coverOeuvre")?.absUrl("src")
        author = document.selectFirst("span.info-lbl:contains(Créateur) + span.info-val")?.text()
        status = parseStatus(document.selectFirst("span.info-lbl:contains(État) + span.info-val")?.text())
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "en cours" -> SManga.ONGOING
        "terminé" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ========================= Chapters =========================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .flatMap { response ->
                val url = response.request.url
                val document = response.asJsoup()
                val scanPanelContent = getScanPanelContent(document)

                val observables = scanPanelContent.mapNotNull { line ->
                    val matchResult = SCAN_PANEL_REGEX.find(line) ?: return@mapNotNull null
                    val (scanTitle, scanUrl) = matchResult.destructured
                    if (scanUrl.contains("va")) return@mapNotNull null

                    val scanlatorGroup = scanTitle.replace(SCANS_REGEX, "").trim()
                    val subMangaRequest = GET(
                        url.newBuilder().addPathSegments(scanUrl).build(),
                        headers,
                    )

                    client.newCall(subMangaRequest).asObservableSuccess()
                        .flatMap { res ->
                            val subDocument = res.asJsoup()
                            val mainPageLink = subDocument.selectFirst("a:has(#imgOeuvre.grayscale)")?.absUrl("href")

                            val titleObservable = if (!mainPageLink.isNullOrEmpty()) {
                                client.newCall(GET(mainPageLink, headers)).asObservableSuccess()
                                    .map { mainRes ->
                                        mainRes.asJsoup().getWorkTitle()
                                    }
                                    .onErrorReturn { "" }
                            } else {
                                Observable.just("")
                            }

                            titleObservable.flatMap { fetchedTitle ->
                                var title = fetchedTitle
                                if (title.isBlank()) {
                                    title = subDocument.getWorkTitle()
                                }

                                if (title.isBlank()) {
                                    Observable.just(emptyList<SChapter>())
                                } else {
                                    val chapterUrl = "$baseUrl/s2/scans/get_nb_chap_et_img.php".toHttpUrl()
                                        .newBuilder()
                                        .addQueryParameter("oeuvre", title)
                                        .build()

                                    client.newCall(GET(chapterUrl, headers)).asObservableSuccess()
                                        .map { apiResponse ->
                                            val apiImageCountJson = try {
                                                apiResponse.parseAs<Map<String, Int>>()
                                            } catch (e: Exception) {
                                                emptyMap()
                                            }

                                            val parsedChapterList = mutableListOf<SChapter>()
                                            var chapterDelay = 0
                                            val html = subDocument.html()
                                            if (html.contains("resetListe()")) {
                                                val scriptCommandList = html.split(";")
                                                scriptCommandList.forEach { command ->
                                                    when {
                                                        CREATE_LIST_REGEX.find(command) != null -> {
                                                            val data = CREATE_LIST_REGEX.find(command)!!.groupValues[1].split(",")
                                                            val start = data[0].trim().toInt()
                                                            val end = data[1].trim().toInt()

                                                            for (i in start..end) {
                                                                parsedChapterList.add(createChapter(i.toString(), parsedChapterList.size + 1, title, scanlatorGroup, chapterUrl))
                                                            }
                                                        }

                                                        NEW_SP_REGEX.find(command) != null -> {
                                                            val chapterName = NEW_SP_REGEX.find(command)!!.groupValues[1]
                                                            parsedChapterList.add(createChapter(chapterName, parsedChapterList.size + 1, title, scanlatorGroup, chapterUrl))
                                                            chapterDelay++
                                                        }
                                                    }
                                                }
                                            }

                                            (parsedChapterList.size until apiImageCountJson.size).forEach { _ ->
                                                parsedChapterList.add(createChapter((parsedChapterList.size + 1 - chapterDelay).toString(), parsedChapterList.size + 1, title, scanlatorGroup, chapterUrl))
                                            }
                                            parsedChapterList
                                        }
                                }
                            }
                        }
                        .onErrorReturn { emptyList() }
                }

                if (observables.isEmpty()) {
                    Observable.just(emptyList<SChapter>())
                } else {
                    Observable.zip(observables) { arrays ->
                        val parsedChapterList = mutableListOf<SChapter>()
                        arrays.forEach { anyList ->
                            @Suppress("UNCHECKED_CAST")
                            val chapters = anyList as List<SChapter>
                            for (chapter in chapters) {
                                if (parsedChapterList.none { it.name == chapter.name }) {
                                    parsedChapterList.add(chapter)
                                }
                            }
                        }
                        parsedChapterList.sortBy { chapter -> "$baseUrl${chapter.url}".toHttpUrl().queryParameter("id")?.toIntOrNull() }
                        parsedChapterList.asReversed()
                    }
                }
            }
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // ========================= Pages =========================
    override fun pageListRequest(chapter: SChapter): Request {
        val url = (baseUrl + chapter.url).toHttpUrl()
        val chapterId = url.queryParameter("id")
        return GET(url.newBuilder().fragment(chapterId).build(), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url
        val title = url.queryParameter("oeuvre")
        val chapterId = url.fragment

        val apiImageCountJson = response.parseAs<Map<String, Int>>()
        val imageCount = apiImageCountJson[chapterId] ?: 0

        return (1..imageCount).map { index ->
            Page(index, imageUrl = "$baseUrl/s2/scans/$title/$chapterId/$index.jpg")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .add("Referer", "$baseUrl/")
            .build()

        return GET(page.imageUrl!!, imgHeaders)
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

    // ========================= Utilities =========================
    private fun parseMangasPage(response: Response, containerSelector: String, urlSuffixToRemove: String = ""): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(containerSelector).map {
            SManga.create().apply {
                title = it.selectFirst("h2.card-title")!!.text()
                val url = it.selectFirst("a")!!.absUrl("href")
                setUrlWithoutDomain(if (urlSuffixToRemove.isNotEmpty()) url.removeSuffix(urlSuffixToRemove) else url)
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("#list_pagination > a.bg-sky-900 + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun getScanPanelContent(document: Document): List<String> {
        val scriptContent = document.select("script:containsData(panneauScan(\"nom\", \"url\"))").toString()
        return scriptContent.split(";").drop(1)
    }

    private fun Document.getWorkTitle(): String = selectFirst("#titreOeuvre")?.textNodes()?.firstOrNull()?.wholeText ?: ""

    private fun createChapter(chapterName: String, id: Int, title: String, scanlatorGroup: String, chapterUrl: HttpUrl): SChapter = SChapter.create().apply {
        name = "Chapitre $chapterName"
        setUrlWithoutDomain(chapterUrl.newBuilder().addQueryParameter("id", id.toString()).addQueryParameter("title", title).build().toString())
        scanlator = scanlatorGroup
    }

    companion object {
        private val SCAN_PANEL_REGEX = """panneauScan\("(.+?)", "(.+?)"\)""".toRegex()
        private val CREATE_LIST_REGEX = """creerListe\((\d+,\s*\d+)\)""".toRegex()
        private val NEW_SP_REGEX = """newSP\((\d+(\.\d+)?|"(.*?)")\)""".toRegex()
        private val SCANS_REGEX = """(Scans|\(|\))""".toRegex()
    }
}
