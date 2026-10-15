package eu.kanade.tachiyomi.extension.fr.animesama

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class AnimeSama : KeiSource() {

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("Accept-Language", "fr-FR")

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/catalogue".toHttpUrl().newBuilder()
            .addQueryParameter("type[]", "Scans")
            .addQueryParameter("page", page.toString())
            .build()
        return parseMangasPage(client.get(url).asJsoup(), "div#list_catalog > div")
    }

    // ========================= Latest =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangasPage(client.get(baseUrl).asJsoup(), "div#containerAjoutsScans > div", "scan/vf/")

    // ========================= Search =========================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.pathSegments.contains("catalogue")) return null

        val path = url.encodedPath.removeSuffix("/").substringBefore("/scan")
        val mangaUrl = url.newBuilder().encodedPath("$path/").build()
        return detailsParse(client.get(mangaUrl).asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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
        return parseMangasPage(client.get(url).asJsoup(), "div#list_catalog > div")
    }

    private fun detailsParse(document: Document): SManga? {
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
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val url = response.request.url
        val document = response.asJsoup()

        manga.populateMangaDetails(document)

        return SMangaUpdate(
            manga,
            if (fetchChapters) fetchChapterList(url, document) else chapters,
        )
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
    private suspend fun fetchChapterList(url: HttpUrl, document: Document): List<SChapter> {
        val scanPanelContent = getScanPanelContent(document)

        val chapterLists = coroutineScope {
            scanPanelContent.mapNotNull { line ->
                val matchResult = SCAN_PANEL_REGEX.find(line) ?: return@mapNotNull null
                val (scanTitle, scanUrl) = matchResult.destructured
                if (scanUrl.contains("va")) return@mapNotNull null

                val scanlatorGroup = scanTitle.replace(SCANS_REGEX, "").trim()
                async {
                    try {
                        fetchScanChapters(url.newBuilder().addPathSegments(scanUrl).build(), scanlatorGroup)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
        }

        val parsedChapterList = mutableListOf<SChapter>()
        chapterLists.forEach { chapters ->
            for (chapter in chapters) {
                if (parsedChapterList.none { it.name == chapter.name }) {
                    parsedChapterList.add(chapter)
                }
            }
        }
        parsedChapterList.sortBy { chapter -> "$baseUrl${chapter.url}".toHttpUrl().queryParameter("id")?.toIntOrNull() }
        return parsedChapterList.asReversed()
    }

    private suspend fun fetchScanChapters(scanUrl: HttpUrl, scanlatorGroup: String): List<SChapter> {
        val subDocument = client.get(scanUrl).asJsoup()
        val mainPageLink = subDocument.selectFirst("a:has(#imgOeuvre.grayscale)")?.absUrl("href")

        var title = if (!mainPageLink.isNullOrEmpty()) {
            try {
                client.get(mainPageLink).asJsoup().getWorkTitle()
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }
        if (title.isBlank()) {
            title = subDocument.getWorkTitle()
        }

        if (title.isBlank()) return emptyList()

        val chapterUrl = "$baseUrl/s2/scans/get_nb_chap_et_img.php".toHttpUrl()
            .newBuilder()
            .addQueryParameter("oeuvre", title)
            .build()

        val apiResponse = client.get(chapterUrl)
        val apiImageCountJson = try {
            apiResponse.parseAs<Map<String, Int>>()
        } catch (_: Exception) {
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
        return parsedChapterList
    }

    // ========================= Pages =========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = (baseUrl + chapter.url).toHttpUrl()
        val title = url.queryParameter("oeuvre")
        val chapterId = url.queryParameter("id")

        val apiImageCountJson = client.get(url).parseAs<Map<String, Int>>()
        val imageCount = apiImageCountJson[chapterId] ?: 0

        return (1..imageCount).map { index ->
            Page(index, imageUrl = "$baseUrl/s2/scans/$title/$chapterId/$index.jpg")
        }
    }

    // ========================= Filters =========================
    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/catalogue").asJsoup()
        return document.select("#list_genres #genreList label").mapNotNull { labelElement ->
            val input = labelElement.selectFirst("input[name=genre[]]") ?: return@mapNotNull null
            val labelText = labelElement.selectFirst("span")?.text() ?: return@mapNotNull null
            val value = input.attr("value")
            labelText to value
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genreList = data?.parseAs<List<Pair<String, String>>>().orEmpty()

        return if (genreList.isNotEmpty()) {
            FilterList(GenreFilter(genreList))
        } else {
            FilterList()
        }
    }

    // ========================= Utilities =========================
    private fun parseMangasPage(document: Document, containerSelector: String, urlSuffixToRemove: String = ""): MangasPage {
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
