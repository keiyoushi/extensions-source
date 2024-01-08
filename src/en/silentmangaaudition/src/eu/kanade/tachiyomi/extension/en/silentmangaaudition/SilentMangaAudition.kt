package eu.kanade.tachiyomi.extension.en.silentmangaaudition

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class SilentMangaAudition : HttpSource() {

    override val name = "Silent Manga Audition"

    override val baseUrl = "https://manga-audition.com"

    override val lang = "en"

    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val entries = SMA_ENTRIES.mapIndexed { i, entry -> entry.toSManga(i) }
        return Observable.just(MangasPage(entries, false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val filteredEntries = SMA_ENTRIES
            .mapIndexed { i, entry -> entry.toSManga(i) }
            .filter {
                it.title.contains(query, true) ||
                    it.description!!.contains(query, true)
            }

        return Observable.just(MangasPage(filteredEntries, false))
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val index = manga.url.substringAfterLast(",").toInt()
        val entry = SMA_ENTRIES[index]

        return Observable.just(entry.toSManga(index))
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = manga.url.substringBefore(",")
        return GET(if (url.startsWith("/")) baseUrl + url else url, headers)
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = manga.url
            .substringAfter(",")
            .substringBefore(",")

        return GET(SMACMAG_URL + url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup()
            .select(chapterListSelector())
            .map { chapterFromElement(it) }
    }

    private fun chapterListSelector(): String = "ol.playlist li a"

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("span.ttl").text()
        scanlator = element.select("span.name").text()
        url = element.attr("abs:href")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterUrl = response.request.url.toString()

        return response.asJsoup()
            .select("div.swiper-wrapper div.swiper-slide img.swiper-lazy")
            .mapIndexed { i, element -> Page(i, chapterUrl, element.attr("data-src")) }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException("Not used")

    companion object {
        private const val SMACMAG_URL = "https://smacmag.net"

        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/106.0.0.0 Safari/537.36"
    }
}
