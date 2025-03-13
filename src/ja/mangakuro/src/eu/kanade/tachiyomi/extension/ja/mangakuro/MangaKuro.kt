package eu.kanade.tachiyomi.extension.ja.mangakuro

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaKuro : ParsedHttpSource() {

    override val name = "MangaKuro"

    override val baseUrl = "https://mangakuro.net"

    override val lang = "ja"

    override val supportsLatest = true

    override fun popularMangaSelector() = ".story_item"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaNextPageSelector() = "a[title='Last Page']"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    private fun mangaRequestBuilder(type: String, page: Int, key: String, value: String) = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(type)
            addPathSegment(page.toString())
            addQueryParameter(key, value)
        }.build(),
        headers,
    )

    override fun popularMangaRequest(page: Int): Request = mangaRequestBuilder("all-manga", page, "sort", "views")

    override fun latestUpdatesRequest(page: Int): Request = mangaRequestBuilder("all-manga", page, "sort", "latest-updated")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = mangaRequestBuilder("search", page, "keyword", query)

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            title = element.selectFirst(".mg_name a")!!.text()
            thumbnail_url = element.selectFirst("img")!!.absUrl("src")
        }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        author = document.selectFirst("div:has(.lnr-user) + .info_value")?.text()
        status = document.selectFirst("div:has(.lnr-leaf) + .info_value")?.text()
            .orEmpty().let { parseStatus(it) }
        description = document.selectFirst(".detail_reviewContent")?.text()
        thumbnail_url = document.selectFirst(".detail_avatar img")?.absUrl("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("進行中") -> SManga.ONGOING
        // status.contains("Completed") -> SManga.COMPLETED / I only found OnGoing Titles and i have no idea what string they would use
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = ".chapter_box .item"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val a = element.selectFirst("a")!!
        setUrlWithoutDomain(a.absUrl("href"))
        name = a.text().substringAfter("# ")
        // date_upload = no real date in web
    }

    private val chapterIDRegex = """CHAPTER_ID = (\d+);""".toRegex()
    private val imageRegex = """src=\\"([^"]+)\\""".toRegex()

    override fun pageListParse(document: Document): List<Page> {
        val id = chapterIDRegex.find(document.outerHtml())?.groupValues?.get(1) ?: return listOf()

        val response = client.newCall(GET("$baseUrl/ajax/image/list/chap/$id", headers)).execute()
        return imageRegex.findAll(response.body.string()).mapIndexed { idx, img ->
            Page(idx, imageUrl = img.groupValues[1])
        }.toList()
    }

    override fun imageUrlParse(document: Document): String = ""
}
