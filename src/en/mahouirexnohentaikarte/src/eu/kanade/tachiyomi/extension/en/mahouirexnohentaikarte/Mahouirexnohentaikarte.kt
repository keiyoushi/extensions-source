package eu.kanade.tachiyomi.extension.en.mahouirexnohentaikarte

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter

@Source
abstract class Mahouirexnohentaikarte : KeiSource() {

    override val supportsLatest = false

    private fun singleManga() = SManga.create().apply {
        url = MANGA_URL
        title = TITLE
        thumbnail_url = "$baseUrl/storage/manga/cover.webp"
    }

    override suspend fun getPopularManga(page: Int) = MangasPage(listOf(singleManga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = listOf(singleManga()).filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        return singleManga()
    }

    override fun getMangaUrl(manga: SManga) = baseUrl

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document) = singleManga().apply {
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("picture img")?.attr("abs:src") ?: thumbnail_url
        author = document.infoValue("Author")
        genre = document.select("h1 ~ ul li").joinToString { it.text() }
        status = when (document.infoValue("Status")?.lowercase()) {
            "on going", "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = buildString {
            document.selectFirst(".synopsis")?.text()?.let { append(it) }
            document.selectFirst("h1 + p")?.text()?.let { append("\n\nAlternative titles: ", it) }
        }.trim()
    }

    private fun Document.infoValue(label: String) = selectFirst("dl div:has(> dt:containsOwn($label)) > dd")?.text()

    private fun parseChapterList(document: Document) = document.select("#chapters ol > li > a").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = element.attr("title").ifEmpty { element.selectFirst("span span")!!.text() }
            date_upload = dateFormat.tryParseDate(element.selectFirst("time")?.attr("datetime"))
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("img[src*=/storage/manga/chapter-]").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    companion object {
        // matches the url saved by the old madara source so library entries keep working
        private const val MANGA_URL = "/manga/mahoui-rex-no-hentai-karte/"
        private const val TITLE = "Mahoui Rex no Hentai Karte"
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
