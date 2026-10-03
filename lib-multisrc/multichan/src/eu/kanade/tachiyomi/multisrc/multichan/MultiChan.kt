package eu.kanade.tachiyomi.multisrc.multichan

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

abstract class MultiChan : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/mostfavorites?offset=${20 * (page - 1)}").asJsoup()
        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = document.select(popularMangaNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun latestUpdatesUrl(page: Int) = "$baseUrl/manga/new?offset=${20 * (page - 1)}"

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(latestUpdatesUrl(page)).asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }
        val hasNextPage = document.select(latestUpdatesNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    open fun popularMangaSelector() = "div.content_row"

    open fun latestUpdatesSelector() = popularMangaSelector()

    open fun searchMangaSelector() = popularMangaSelector()

    open fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        manga.title = element.attr("title")
        element.selectFirst("h2 > a")?.let {
            manga.setUrlWithoutDomain(it.attr("abs:href"))
        }
        return manga
    }

    open fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    open fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    open fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    open fun popularMangaNextPageSelector() = "a:contains(Вперед)"

    open fun searchMangaNextPageSelector() = "a:contains(Далее)"

    private fun searchGenresNextPageSelector() = popularMangaNextPageSelector()

    protected abstract fun searchMangaUrl(page: Int, query: String, filters: FilterList): String

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val document = client.get(searchMangaUrl(page, query, filters)).asJsoup()
        var hasNextPage = false

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val nextSearchPage = document.select(searchMangaNextPageSelector())
        if (nextSearchPage.isNotEmpty()) {
            val query = document.selectFirst("input#searchinput")?.attr("value") ?: ""
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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), fetchChapterList(manga, document))
    }

    open fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("#info_wrap tr,#info_wrap > div")
        val descElement = document.selectFirst("div#description")
        val rawCategory = infoElement.select(":contains(Тип) a").text().lowercase()
        val manga = SManga.create()
        manga.title = document.select("title").text().substringBefore(" »")
        manga.author = infoElement.select(":contains(Автор) .item2").text()
        manga.genre = rawCategory + ", " + document.select(".sidetags ul a:last-child").joinToString { it.text() }
        manga.status = parseStatus(infoElement.select(":contains(Загружено)").text())
        manga.description = descElement?.textNodes()?.firstOrNull()?.text()
        manga.thumbnail_url = document.selectFirst("img#cover")?.attr("abs:src")
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("перевод завершен") -> SManga.COMPLETED
        element.contains("перевод продолжается") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    protected open suspend fun fetchChapterList(manga: SManga, mangaPage: Document): List<SChapter> = mangaPage.select(chapterListSelector()).map { element ->
        chapterFromElement(element)
    }

    open fun chapterListSelector() = "table.table_cha tr:gt(1)"

    open fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.selectFirst("a")!!

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("abs:href"))
        chapter.name = urlElement.text()
        chapter.chapter_number = chapterNumberRegex.find(chapter.name)?.groupValues?.get(2)?.toFloat() ?: -1F
        chapter.date_upload = element.selectFirst("div.date")?.text().let { DATE_FORMAT.tryParseDate(it) }
        return chapter
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListParse(client.get(baseUrl + chapter.url).body.string())

    protected open fun pageListParse(html: String): List<Page> {
        val beginIndex = html.indexOf("fullimg\":[") + 10
        val endIndex = html.indexOf(",]", beginIndex)
        val trimmedHtml = html.substring(beginIndex, endIndex).replace("\"", "")
        val pageUrls = trimmedHtml.split(',')

        return pageUrls.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
        private val chapterNumberRegex = "(глава\\s|часть\\s)([0-9]+\\.?[0-9]*)".toRegex(RegexOption.IGNORE_CASE)
    }
}
