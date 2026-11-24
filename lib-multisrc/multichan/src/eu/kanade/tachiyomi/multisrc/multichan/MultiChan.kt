package eu.kanade.tachiyomi.multisrc.multichan

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

abstract class MultiChan : KeiSource() {

    /** The site's domains; the first one's robots.txt names the current host */
    protected abstract val siteDomains: List<String>

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    /** The current host named in robots.txt */
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> {
        val robots = client.get("https://${siteDomains.first()}/robots.txt", Fetch.DOCUMENT).body.string()
        return listOf("https://" + robotsHostRegex.find(robots)!!.groupValues[1])
    }

    protected open fun popularMangaUrl(page: Int) = "$baseUrl/mostfavorites?offset=${20 * (page - 1)}"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(popularMangaUrl(page), Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(popularMangaSelector()).map(::popularMangaFromElement)
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun latestUpdatesUrl(page: Int) = "$baseUrl/manga/new?offset=${20 * (page - 1)}"

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(latestUpdatesUrl(page), Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map(::latestUpdatesFromElement)
        val hasNextPage = document.selectFirst(latestUpdatesNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    open fun popularMangaSelector() = "div.content_row"

    open fun latestUpdatesSelector() = popularMangaSelector()

    open fun searchMangaSelector() = popularMangaSelector()

    open fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        title = element.attrOrNull("title")!!
        setUrlWithoutDomain(element.selectFirst("h2 > a")!!.attr("abs:href"))
    }

    open fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    open fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    open fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    open fun popularMangaNextPageSelector() = "a:contains(Вперед)"

    // Text search pages link "Далее", tag pages "Вперед"
    open fun searchMangaNextPageSelector() = "a:contains(Далее), a:contains(Вперед)"

    protected abstract fun searchMangaUrl(page: Int, query: String, filters: FilterList): String

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val document = client.get(searchMangaUrl(page, query, filters), Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(searchMangaSelector()).map(::searchMangaFromElement)
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val isSiteHost = url.host == baseUrl.toHttpUrl().host || siteDomains.any { url.host == it || url.host.endsWith(".$it") }
        val segments = url.pathSegments
        if (!isSiteHost || segments.size != 2 || segments[0] != "manga" || !segments[1].endsWith(".html")) return null
        val manga = SManga.create().apply { this.url = url.encodedPath }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga.apply { this.url = manga.url }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), document.select(chapterListSelector()).map(::chapterFromElement))
    }

    open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.select("#info_wrap .row, #info_wrap tr").mapNotNull { row ->
            val label = row.selectFirst(".item") ?: return@mapNotNull null
            label.text() to (row.selectFirst(".item2") ?: return@mapNotNull null)
        }.toMap()

        title = document.selectFirst("title")!!.text().substringBefore(" »")
        author = info["Автор"]?.cleanText()
        genre = (listOfNotNull(info["Тип"]?.cleanText()) + document.select(".sidetags ul a:last-child").eachText())
            .joinToString()
        status = parseStatus(info["Загружено"]?.text())
        description = MarkdownDescription.Builder()
            .description(
                document.selectFirst("div#description")?.clone()?.apply {
                    // "Прислать описание" (submit a description) link
                    select("div:has(> a[href^=/description])").remove()
                },
            )
            .altTitles(listOfNotNull(info["Другие названия"]?.cleanText()), "Другие названия")
            .apply {
                INFO_FIELDS.forEach { label -> field(label, info[label]?.cleanText()) }
            }
            .build()
        thumbnail_url = document.selectFirst("img#cover")?.attr("abs:src")
    }

    open fun chapterListSelector() = "table.table_cha tr:gt(1)"

    open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val urlElement = element.selectFirst("a")!!
        setUrlWithoutDomain(urlElement.attr("abs:href"))
        name = urlElement.text()
        chapter_number = chapterNumberRegex.find(name)?.groupValues?.get(2)?.toFloat() ?: -1F
        date_upload = DATE_FORMAT.tryParseDate(element.selectFirst("div.date")?.text())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListParse(client.get(getChapterUrl(chapter), Fetch.DOCUMENT).body.string())

    protected open fun pageListParse(html: String): List<Page> {
        val beginIndex = html.indexOf("fullimg\":[") + 10
        val endIndex = html.indexOf(",]", beginIndex)
        val trimmedHtml = html.substring(beginIndex, endIndex).replace("\"", "")
        val pageUrls = trimmedHtml.split(',')

        return pageUrls.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    private fun parseStatus(text: String?): Int = when {
        text == null -> SManga.UNKNOWN
        text.contains("перевод завершен") -> SManga.COMPLETED
        text.contains("перевод продолжается") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    private fun Element.cleanText(): String? = text().replace("\uFEFF", "").trim().ifEmpty { null }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)

        @JvmStatic
        protected val chapterNumberRegex = """(глава\s|часть\s)([0-9]+\.?[0-9]*)""".toRegex(RegexOption.IGNORE_CASE)
        private val robotsHostRegex = """(?im)^host:\s*(\S+)""".toRegex()

        // Info rows shown under the description, by the site's label
        private val INFO_FIELDS = listOf("Аниме/манга", "Цикл/Группа", "Статус (Томов)", "Переводчик", "Переводчики", "Язык")
    }
}
