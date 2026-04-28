package eu.kanade.tachiyomi.extension.all.buondua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.randomua.UserAgentType
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class BuonDua : HttpSource() {
    override val baseUrl = "https://buondua.com"
    override val lang = "all"
    override val name = "Buon Dua"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 10, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .setRandomUserAgent(UserAgentType.MOBILE)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?start=${20 * (page - 1)}", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangasPage(response)

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/hot?start=${20 * (page - 1)}", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangasPage(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tagFilter = filters.firstInstanceOrNull<Filter.Text>()
        return when {
            query.isNotEmpty() -> GET("$baseUrl/?search=$query&start=${20 * (page - 1)}", headers)
            tagFilter?.state?.isNotEmpty() == true -> GET("$baseUrl/tag/${tagFilter.state}&start=${20 * (page - 1)}", headers)
            else -> popularMangaRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangasPage(response)

    private fun parseMangasPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".blog > div").mapNotNull { element ->
            val link = element.selectFirst(".item-content .item-link") ?: return@mapNotNull null
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                title = link.text()
                setUrlWithoutDomain(link.attr("abs:href"))
            }
        }
        val hasNextPage = document.selectFirst(".pagination-next:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Details
    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".article-header")?.text() ?: ""
            description = document.selectFirst(".article-info > strong")?.text()
            genre = document.selectFirst(".article-tags")?.select(".tags > .tag")
                ?.joinToString(", ") { it.text().substringAfter("#") }
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val dateUploadStr = doc.selectFirst(".article-info > small")?.text()
        val dateUpload = DATE_FORMAT.tryParse(dateUploadStr)

        val maxPage = doc.select("nav.pagination:first-of-type a.pagination-next").last()
            ?.attr("abs:href")
            ?.takeIf { it.startsWith("http") }
            ?.toHttpUrlOrNull()
            ?.queryParameter("page")?.toIntOrNull() ?: 1

        val basePageUrl = response.request.url.toString()

        return (maxPage downTo 1).map { page ->
            SChapter.create().apply {
                setUrlWithoutDomain("$basePageUrl?page=$page")
                name = "Page $page"
                date_upload = dateUpload
            }
        }
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".article-fulltext img").mapIndexed { i, imgEl ->
            Page(i, imageUrl = imgEl.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        object : Filter.Text("Tag ID") {},
    )

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("HH:mm dd-MM-yyyy", Locale.US)
    }
}
