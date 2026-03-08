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
import rx.Observable
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

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = throw Exception("Use search to find comics.")

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

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
        setUrlWithoutDomain(titleAnchor.attr("href"))
        title = titleAnchor.text()
        thumbnail_url = element.selectFirst("img.book-cover")?.attr("src")
    }

    // Manga details

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val archive = response.asJsoup().selectFirst(".description-archive")!!
        title = archive.selectFirst("h1")!!.text()
        thumbnail_url = archive.selectFirst("p img")?.attr("abs:src")
        val infoStrongs = archive.select(".b > p strong")
        genre = infoStrongs.firstOrNull()?.text()
        author = infoStrongs.lastOrNull()?.text()
        description = archive.selectFirst("#hidden-description")?.also { el ->
            el.select("hr").prepend("\\n")
        }?.text()?.replace("\\n", "\n\n")
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select(".list-story a").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.text()
            val year = name.substringAfterLast('(').substringBefore(')')
            date_upload = dateFormat.tryParse("$year-1-1")
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("body img:not(div[id=logo] img)").mapIndexed { idx, element ->
        Page(idx, imageUrl = element.attr("abs:src"))
    }

    override fun imageUrlParse(response: Response) = ""

    // Latest (unsupported)

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
