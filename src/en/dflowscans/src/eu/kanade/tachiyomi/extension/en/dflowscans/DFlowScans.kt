package eu.kanade.tachiyomi.extension.en.dflowscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class DFlowScans : HttpSource() {
    override val name = "DFlowScans"
    override val baseUrl = "https://dflow.alwaysdata.net"
    override val lang = "en"
    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)

    override val client = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/Series", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val statusFilter = filters.firstInstance<StatusFilter>()
        val url = "$baseUrl/Series".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("status", statusFilter.value)
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".col-lg-3.col-md-4.col-sm-6").map {
            SManga.create().apply {
                title = it.selectFirst(".manga-card-title")!!.text()
                thumbnail_url = it.selectFirst(".manga-card-image img")?.absUrl("src")
                setUrlWithoutDomain(it.selectFirst(".manga-card-body a.btn")!!.absUrl("href"))
            }
        }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst(".col-md-4.col-lg-3 img")?.absUrl("src")
            description = document.selectFirst(".col-md-8.col-lg-9 > p")?.text()
            genre = document.select("div:has(> strong:containsOwn(Genres)) span")?.joinToString { it.text() }
            author = document.selectFirst("div:has(> span:containsOwn(Author)) span + span")?.text()
            artist = document.selectFirst("div:has(> span:containsOwn(Artist)) span + span")?.text()
            status = when (document.selectFirst("div:has(> span:containsOwn(Status)) span + span")?.text()) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                "Hiatus" -> SManga.ON_HIATUS
                "Dropped" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div#chapters-section div:has(> a.btn-primary)").map {
            SChapter.create().apply {
                val chapterTitle = it.selectFirst("h5")!!.text()
                val subTitle = it.selectFirst("p:not(:has(i.fa-calendar))")?.text()
                    ?.removeSuffix(" - $chapterTitle")
                    ?.removeSuffix(" $chapterTitle")
                name = if (subTitle.isNullOrEmpty()) chapterTitle else "$chapterTitle - $subTitle"
                val date = it.selectFirst("p:has(i.fa-calendar)")?.text()
                date_upload = dateFormat.tryParse(date)
                setUrlWithoutDomain(it.selectFirst("a.btn-primary")!!.absUrl("href"))
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(const pages)")!!.data()
        val json = script.substringAfter("const pages = ").substringBefore(";")
        return json.parseAs<List<Dto>>().map {
            Page(it.num - 1, imageUrl = it.url)
        }
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        StatusFilter(),
    )

    private class StatusFilter :
        SelectFilter(
            "Status",
            arrayOf(
                Pair("All", ""),
                Pair("Ongoing", "Ongoing"),
                Pair("Completed", "Completed"),
                Pair("Hiatus", "Hiatus"),
            ),
        )

    private open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val value: String
            get() = vals[state].second
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
