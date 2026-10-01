package eu.kanade.tachiyomi.extension.it.zeurelscan

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
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("d/M/yyyy")

@Source
abstract class ZeurelScan : KeiSource() {

    // Popular (not actually sorted as site has no such functionality)

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(fetchSeries(), false)

    private suspend fun fetchSeries(): List<SManga> = client.get("$baseUrl/series").asJsoup().select("a.series-card").map {
        SManga.create().apply {
            setUrlWithoutDomain(it.absUrl("href"))
            title = it.select("span.series-title").text()
            thumbnail_url = it.select("img").attr("src")
        }
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/ultimi").asJsoup()
        // Cache titles for deduplication
        val titles = mutableListOf<String>()

        val latestManga = document.select("a.latest-row").mapNotNull { element ->
            // Deduplicate entries, add only not-already-seen
            if (!titles.any { title -> title.contains(element.select("span.latest-title").text()) }) {
                titles += element.select("span.latest-title").text()
                SManga.create().apply {
                    // Rows link to the chapter reader (/read/<slug>/<chapter>)
                    url = "/serie/" + element.absUrl("href").toHttpUrl().pathSegments[1]
                    title = element.select("span.latest-title").text()
                    thumbnail_url = element.select("img.latest-thumb").attr("src")
                }
            } else {
                null
            }
        }
        return MangasPage(latestManga, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(fetchSeries().filter { it.title.contains(query, true) }, false)

    // Details

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(document, manga.url), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document, mangaUrl: String) = SManga.create().apply {
        val info = document.selectFirst("div.series-header")!!

        url = mangaUrl
        title = info.selectFirst("h1")!!.text().trim()
        author = info.selectFirst("p:contains(Autore)")!!.wholeOwnText().trim()
        artist = info.selectFirst("p:contains(Artista)")!!.wholeOwnText().trim()
        genre = info.selectFirst("p:contains(Genere)")!!.wholeOwnText().trim()
        description = info.selectFirst("p.series-plot")!!.text().trim()
        thumbnail_url = document.select("img").attr("abs:src")

        status = parseStatus(info.selectFirst("p:contains(Stato)")!!.wholeOwnText())
    }

    private fun parseStatus(status: String) = when {
        status.contains("In Corso", true) -> SManga.ONGOING
        status.contains("Completa", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    private fun parseChapterList(document: Document): List<SChapter> {
        val list = document.select("div.chapter:has(a)")
        var lastChapter = 0f
        return list.map {
            val str = it.selectFirst("a")!!.wholeOwnText().substringAfter("#")

            // alternative (incremental, no side story handling)
            // val chapterNumStr = it.dataset().get("pagina")
            val chapterNumStr = str.substringBefore("–").trim()
            val chapterNum = if (chapterNumStr.contains("_")) {
                chapterNumStr.substringBefore("_").toFloat() + 0.1f
            } else {
                try {
                    chapterNumStr.toFloat()
                } catch (e: NumberFormatException) {
                    // Handle chapters with non-number chapter number
                    lastChapter + 0.1f
                }
            }
            lastChapter = chapterNum

            val tmpTitle = str.substringAfter("–").trim()
            val title = if (tmpTitle.length != 0) {
                chapterNumStr + " - " + tmpTitle
            } else {
                "Capitolo " + chapterNumStr
            }
            SChapter.create().apply {
                setUrlWithoutDomain(it.selectFirst("a")!!.absUrl("href"))
                name = title
                // text is "dd/MM/yyyy – <views>"
                date_upload = dateFormat.tryParseDate(it.selectFirst("span.chapter-date")!!.text().substringBefore(" "))
                chapter_number = chapterNum
            }
        }
    }

    // Continue parsing even if the server returns HTTP 400
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), ensureSuccess = false).asJsoup()
        .select("div.reader img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
}
