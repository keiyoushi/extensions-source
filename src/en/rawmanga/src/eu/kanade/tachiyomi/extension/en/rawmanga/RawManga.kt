package eu.kanade.tachiyomi.extension.en.rawmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class RawManga : ParsedHttpSource() {

    override val name = "Raw-Manga"
    override val baseUrl = "https://raw-manga.org"
    override val lang = "en"
    override val supportsLatest = true

    // Popular Manga

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/pieces/pages.php?orderedpage=$page")
    }

    override fun popularMangaSelector(): String = ".mangalistcnt"

    override fun popularMangaNextPageSelector(): String = "#paginationcontainer a:contains(Last Page)"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.selectFirst("div > a")!!.attr("href")
            title = element.select("div > a > h2").text().trim()
            thumbnail_url = element.select("a > div > img").attr("src")
        }
    }

    // Latest Updates

    override fun latestUpdatesSelector(): String = ".updatescontainer"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.select(".mangatitle").attr("href")
            title = element.select(".mangatitle").text().trim()
            thumbnail_url = element.select("div > a > img").attr("src")
        }
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/updates.php?page=$page)", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        return MangasPage(mangas, mangas.size == 60)
    }

    // Manga Details Parse

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            thumbnail_url = document.select(".img-thumbnail").attr("src")
            title = document.select("#mangaholder h1").text().trim()
            description = document.selectFirst("#info p")!!.text()
                .replace(Regex("^(.*?)This Comic is About"), "").trim() // Removing boilerplate text
            author = document.selectFirst("#titlecompenents dt")!!.text().trim()
            genre = document.select("#titlecompenents li").text().trim()
            status = when (document.select("#titlecompenents dt:nth-of-type(2)").text().trim()) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // Chapter List

    override fun chapterListSelector(): String = "tbody tr"

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaId = response.asJsoup().selectFirst(".btn-danger")!!.attr("onclick")
            .substringAfter("showAllCHaps(").substringBefore(")")

        val chapters = client.newCall(GET("$baseUrl/pieces/chaps.php?mng=$mangaId", headers)).execute()

        return chapters.asJsoup().select(chapterListSelector())
            .map {
                SChapter.create().apply {
                    url = it.select("a").attr("href").trim()
                    name = "Chapter " + it.text().trim().split(" ")[1]
                    chapter_number = it.text().trim().split(" ")[1].toFloat()
                    date_upload = it.select("td:last-of-type").text().trim()
                        .let { DATE_FORMATTER.parse(it)?.time ?: 0L }
                }
            }
    }

    // Page

    override fun pageListParse(document: Document): List<Page> {
        return document.select("#chapcontainer .imgholder").mapIndexed { index, element ->
            Page(index, "", element.attr("src"))
        }
    }

    // Search
    // advanced.php supports filters via POST, but has no title search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search.php?manga=$query", headers)
    }

    override fun searchMangaSelector(): String = "a"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.attr("href").trim()
            title = element.select("li").text().trim()
            thumbnail_url = "https://readmanganow.com/images/thumbnails/$title.webp" // Works for most
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesNextPageSelector() = throw java.lang.UnsupportedOperationException("Not used.")

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used.")

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used.")

    override fun searchMangaNextPageSelector(): String = throw UnsupportedOperationException("Not used.")

    companion object {
        val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    }
}
