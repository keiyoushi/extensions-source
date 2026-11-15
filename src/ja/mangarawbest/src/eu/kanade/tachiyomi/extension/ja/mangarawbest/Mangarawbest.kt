package eu.kanade.tachiyomi.extension.ja.mangarawbest

import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Source
abstract class Mangarawbest : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, "", "-views", STATUS_ALL)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, "", "-updated_at", STATUS_ALL)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(
        page,
        query.trim(),
        filters.firstInstance<SortFilter>().value,
        filters.firstInstance<StatusFilter>().value,
    )

    private suspend fun getMangaList(page: Int, query: String, sort: String, status: String): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga-list")
            .addQueryParameter("sort", sort)
            .apply { if (query.isNotEmpty()) addQueryParameter("filter[name]", query) }
            .addQueryParameter("filter[status]", status)
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".manga-vertical").map { it.toSManga() }
        val hasNextPage = document.selectFirst("a.paging_prevnext.next > li:not(.disabled)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst("h3 a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = selectFirst("img")?.attrOrNull("abs:data-src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() !in setOf("raw", "manga")) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/raw/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        manga.parseDetails(document)
        return SMangaUpdate(manga, parseChapterList(document))
    }

    private fun SManga.parseDetails(document: Document) {
        val info = document.selectFirst("div:has(> div > h1)")!!
        fun field(label: String) = info.select("div.items-center").firstOrNull { it.selectFirst("span")?.text()?.startsWith(label) == true }
            ?.select("span")?.last()?.text()

        title = info.selectFirst("h1")!!.text()
        thumbnail_url = info.selectFirst("img.cover")?.absUrl("src")
        status = when (field("状態")) {
            "進行中" -> SManga.ONGOING
            "完了" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(
                document.select("div:has(> h3:contains(説明)) p")
                    .map { it.text() }
                    // Older entries open with an SEO line, "<romanized title> Raw free"
                    .filterNot { it.isEmpty() || it.endsWith("Raw free") }
                    .joinToString("\n\n"),
            )
            .altTitles(field("別名")?.split(",").orEmpty().map { it.trim() }.filter { it.isNotEmpty() && it != title }.distinct())
            .build()
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("#chapterList a[href]").map {
        SChapter.create().apply {
            setUrlWithoutDomain(it.absUrl("href"))
            name = it.selectFirst("span")!!.text()
            date_upload = parseRelativeDate(it.select("span").last()?.text())
        }
    }

    private fun parseRelativeDate(text: String?): Long {
        val (amount, unit) = text?.let { RELATIVE_DATE_REGEX.find(it) }?.destructured ?: return 0L
        return ZonedDateTime.now().minus(amount.toLong(), DATE_UNITS.getValue(unit)).toInstant().toEpochMilli()
    }

    // The page list comes encrypted for the site's own decoder, a script it rotates: the reader page runs it
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val host = baseUrl.toHttpUrl().host
        val pages = runWebView<String> {
            userAgent = headers["User-Agent"]!!
            blockImages = true
            val blocked = { WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0))) }
            interceptRequest { request ->
                // The page, its scripts and the decoder's request go through the source's client, without X-Requested-With
                if (request.url.host == host) proxyRequest(request, client) ?: blocked() else blocked()
            }
            poll {
                evaluateJs(PAGES_SCRIPT) { result ->
                    when (result) {
                        "null" -> Unit
                        "\"error\"" -> reject(Exception("The site could not load this chapter's pages"))
                        else -> resolve(result)
                    }
                }
            }
            loadUrl(getChapterUrl(chapter))
        }
        return pages.parseAs<List<String>>().mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
    )

    companion object {
        private const val STATUS_ALL = "2,1"

        private val PAGES_SCRIPT = """
            (function () {
                var images = document.querySelectorAll('#chapter-pages img[data-original]');
                if (images.length) return Array.from(images, function (image) { return image.getAttribute('data-original'); });
                return document.querySelector('[data-cipher-error]:not([hidden])') ? 'error' : null;
            })()
        """.trimIndent()

        private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s*(秒|分|時間|日|週間|ヶ月|年)前""")

        private val DATE_UNITS = mapOf(
            "秒" to ChronoUnit.SECONDS,
            "分" to ChronoUnit.MINUTES,
            "時間" to ChronoUnit.HOURS,
            "日" to ChronoUnit.DAYS,
            "週間" to ChronoUnit.WEEKS,
            "ヶ月" to ChronoUnit.MONTHS,
            "年" to ChronoUnit.YEARS,
        )
    }
}
