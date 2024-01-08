package eu.kanade.tachiyomi.multisrc.multichan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class MultiChan(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(2)
        .build()
    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", baseUrl)
    }
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/mostfavorites?offset=${20 * (page - 1)}", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/new?offset=${20 * (page - 1)}")

    override fun popularMangaSelector() = "div.content_row"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").first()!!.attr("src")
        manga.title = element.attr("title")
        element.select("h2 > a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaNextPageSelector() = "a:contains(Вперед)"

    override fun searchMangaNextPageSelector() = "a:contains(Далее)"

    private fun searchGenresNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        var hasNextPage = false

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val nextSearchPage = document.select(searchMangaNextPageSelector())
        if (nextSearchPage.isNotEmpty()) {
            val query = document.select("input#searchinput").first()!!.attr("value")
            val pageNum = nextSearchPage.let { selector ->
                val onClick = selector.attr("onclick")
                onClick.split("""\\d+""")
            }
            nextSearchPage.attr("href", "$baseUrl/?do=search&subaction=search&story=$query&search_start=$pageNum")
            hasNextPage = true
        }

        val nextGenresPage = document.select(searchGenresNextPageSelector())
        if (nextGenresPage.isNotEmpty()) {
            hasNextPage = true
        }

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("#info_wrap tr,#info_wrap > div")
        val descElement = document.select("div#description").first()!!
        val rawCategory = infoElement.select(":contains(Тип) a").text().lowercase()
        val manga = SManga.create()
        manga.title = document.select("title").text().substringBefore(" »")
        manga.author = infoElement.select(":contains(Автор) .item2").text()
        manga.genre = rawCategory + ", " + document.select(".sidetags ul a:last-child").joinToString { it.text() }
        manga.status = parseStatus(infoElement.select(":contains(Загружено)").text())
        manga.description = descElement.textNodes().first().text().trim()
        manga.thumbnail_url = document.select("img#cover").first()!!.attr("src")
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("перевод завершен") -> SManga.COMPLETED
        element.contains("перевод продолжается") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "table.table_cha tr:gt(1)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()!!

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.chapter_number = "(глава\\s|часть\\s)([0-9]+\\.?[0-9]*)".toRegex(RegexOption.IGNORE_CASE).find(chapter.name)?.groupValues?.get(2)?.toFloat() ?: -1F
        chapter.date_upload = simpleDateFormat.parse(element.select("div.date").first()!!.text())?.time ?: 0L
        return chapter
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val beginIndex = html.indexOf("fullimg\":[") + 10
        val endIndex = html.indexOf(",]", beginIndex)
        val trimmedHtml = html.substring(beginIndex, endIndex).replace("\"", "")
        val pageUrls = trimmedHtml.split(',')

        return pageUrls.mapIndexed { i, url -> Page(i, "", url) }
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlParse(document: Document) = ""

    companion object {
        private val simpleDateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    }
}
