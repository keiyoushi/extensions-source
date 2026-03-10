package eu.kanade.tachiyomi.extension.en.readallcomicscom

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ReadAllComics : HttpSource() {

    override val name = "ReadAllComics"

    override val baseUrl = "https://readallcomics.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) baseUrl else "$baseUrl/?paged=$page"
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.list-story.categories li").map { mangaFromElement(it) }
        val currentPage = document.selectFirst("span.page-numbers.current")?.text()?.trim()?.toIntOrNull() ?: 1
        val hasNextPage = document.select("div.pagination a.page-numbers").any {
            it.attr("abs:href").substringAfter("paged=").substringBefore("#").toIntOrNull()?.let { n -> n > currentPage } == true
        }
        return MangasPage(mangas, hasNextPage)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("story", query)
            addQueryParameter("s", "")
            addQueryParameter("type", "comic")
            if (page > 1) addQueryParameter("paged", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.list-story.categories li").map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst("a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        val titleAnchor = element.selectFirst("a.cat-title")!!
        setUrlWithoutDomain(titleAnchor.attr("abs:href"))
        title = titleAnchor.text()
        thumbnail_url = element.selectFirst("img.book-cover")?.attr("abs:src")
    }

    // Manga details

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val archive = response.asJsoup().selectFirst(".description-archive")!!
        title = archive.selectFirst("h1")?.text() ?: ""
        thumbnail_url = archive.selectFirst("p img")?.attr("abs:src") ?: throw Exception("No cover available")
        val infoStrongs = archive.select(".b > p strong")
        genre = infoStrongs.firstOrNull()?.text()
        author = infoStrongs.lastOrNull()?.text()
        description = archive.selectFirst("#hidden-description")?.wholeText()?.trim()
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select(".list-story a").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = element.text()
            val year = name.substringAfterLast('(').substringBefore(')')
            date_upload = dateFormat.tryParse("$year-1-1")
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("body img:not(div[id=logo] img)").mapIndexed { idx, element ->
        Page(idx, imageUrl = element.attr("abs:src"))
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Latest (unsupported)

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
