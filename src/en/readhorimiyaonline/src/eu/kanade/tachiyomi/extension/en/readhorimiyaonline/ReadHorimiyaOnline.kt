package eu.kanade.tachiyomi.extension.en.readhorimiyaonline

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ReadHorimiyaOnline : KeiSource() {
    override val supportsLatest = false

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get(baseUrl)
        return MangasPage(listOf(parseMangaDetails(response.asJsoup())), false)
    }

    // ========================= Latest =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ========================= Search =========================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val response = client.get(baseUrl)
        return MangasPage(listOf(parseMangaDetails(response.asJsoup())), false)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()

    // ========================= Details =========================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val baseHost = baseUrl.substringAfter("://").substringBefore("/")
        if (url.host != baseHost) return null
        val response = client.get(baseUrl)
        return parseMangaDetails(response.asJsoup())
    }

    // ========================= Chapters + Update =========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(baseUrl)
        val document = response.asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("div#chapter-list a.chapter-list-item").map { element ->
        SChapter.create().apply {
            name = element.selectFirst("span.chapter-name")?.text() ?: element.text()
            date_upload = DATE_FORMATTER.tryParseDate(element.selectFirst("span.chapter-date")?.text())
            setUrlWithoutDomain(element.absUrl("href"))
        }
    }

    // ========================= Pages =========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(baseUrl + chapter.url)
        return parsePageList(response.asJsoup())
    }

    private fun parsePageList(document: Document): List<Page> = document.select("div.images-container img").mapIndexed { i, img ->
        Page(i, "", img.absUrl("src"))
    }

    // ========================= Helpers =========================
    private fun parseMangaDetails(doc: Document): SManga = SManga.create().apply {
        title = "Horimiya"
        url = "/"
        thumbnail_url = doc.selectFirst("img.manga-thumb")?.absUrl("src")
        description = doc.selectFirst("span.desc")?.text()
        genre = doc.select("span.genre-list-item").eachText().joinToString()
        author = metaValue(doc, "Author(s)")
        artist = metaValue(doc, "Artist(s)")
        status = when (metaValue(doc, "Status")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun metaValue(doc: Document, label: String): String? = doc.select("div.extra-manga-info-left p")
        .find { it.text().startsWith(label, ignoreCase = true) }
        ?.selectFirst("span")
        ?.text()

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
    }
}
