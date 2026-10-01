package eu.kanade.tachiyomi.extension.zh.dongmanmanhua

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
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Source
abstract class DongmanManhua : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/dailySchedule").asJsoup()

        val entries = document.select("div#dailyList .daily_section li a, div.daily_lst.comp li a")
            .map(::mangaFromElement)
            .distinctBy { it.url }

        return MangasPage(entries, false)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst("p.subj")!!.text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/dailySchedule?sortOrder=UPDATE&webtoonCompleteType=ONGOING").asJsoup()
        val day = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "div._list_SUNDAY"
            Calendar.MONDAY -> "div._list_MONDAY"
            Calendar.TUESDAY -> "div._list_TUESDAY"
            Calendar.WEDNESDAY -> "div._list_WEDNESDAY"
            Calendar.THURSDAY -> "div._list_THURSDAY"
            Calendar.FRIDAY -> "div._list_FRIDAY"
            Calendar.SATURDAY -> "div._list_SATURDAY"
            else -> "div"
        }

        val entries = document.select("div#dailyList > $day li > a")
            .map(::mangaFromElement)
            .distinctBy { it.url }

        return MangasPage(entries, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("keyword", query)
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        val document = client.get(url).asJsoup()
        val entries = document.select("#content > div.card_wrap.search ul:not(#filterLayer) li a")
            .map(::mangaFromElement)
        val hasNextPage = document.selectFirst("div.more_area, div.paginate a[onclick] + a") != null

        return MangasPage(entries, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = mangaDetailsParse(document).apply { url = manga.url }

        if (!fetchChapters) return SMangaUpdate(updatedManga, chapters)

        return SMangaUpdate(updatedManga, chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document): SManga {
        val detailElement = document.selectFirst(".detail_header .info")
        val infoElement = document.selectFirst("#_asideDetail")

        return SManga.create().apply {
            title = document.selectFirst("h1.subj, h3.subj")!!.text()
            author = detailElement?.selectFirst(".author:nth-of-type(1)")?.ownText()
                ?: detailElement?.selectFirst(".author_area")?.ownText()
            artist = detailElement?.selectFirst(".author:nth-of-type(2)")?.ownText()
                ?: detailElement?.selectFirst(".author_area")?.ownText() ?: author
            genre = detailElement?.select(".genre").orEmpty().joinToString { it.text() }
            description = infoElement?.selectFirst("p.summary")?.text()
            status = with(infoElement?.selectFirst("p.day_info")?.text().orEmpty()) {
                when {
                    contains("更新") -> SManga.ONGOING
                    contains("完结") -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
            thumbnail_url = run {
                val picElement = document.selectFirst("#content > div.cont_box > div.detail_body")
                val discoverPic = document.selectFirst("#content > div.cont_box > div.detail_header > span.thmb")
                picElement?.attr("style")
                    ?.substringAfter("url(")
                    ?.substringBeforeLast(")")
                    ?.removeSurrounding("\"")
                    ?.removeSurrounding("'")
                    ?.takeUnless { it.isBlank() }
                    ?: discoverPic?.selectFirst("img:not([alt='Representative image'])")
                        ?.attr("src")
            }
        }
    }

    private suspend fun chapterListParse(firstPage: Document): List<SChapter> {
        var document = firstPage
        var continueParsing = true
        val chapters = mutableListOf<SChapter>()

        while (continueParsing) {
            document.select("ul#_listUl li").map { chapters.add(chapterFromElement(it)) }
            document.select("div.paginate a[onclick] + a").let { element ->
                if (element.isNotEmpty()) {
                    document = client.get(element.attr("abs:href")).asJsoup()
                } else {
                    continueParsing = false
                }
            }
        }
        return chapters
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.selectFirst("span.subj span")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        date_upload = dateFormat.tryParseDate(element.selectFirst("span.date")?.text())
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-M-d", Locale.ENGLISH)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select("div#_imageList > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("data-url"))
        }
    }
}
