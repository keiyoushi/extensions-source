package eu.kanade.tachiyomi.extension.id.themanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

// Madara to HttpSource
class TheManga : HttpSource() {

    override val name = "TheManga"

    override val baseUrl = "https://themanga.my.id"

    override val lang = "id"

    override val supportsLatest = false

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    // =============================== Popular ================================
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // =============================== Search =================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/explore".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            addQueryParameter("page", page.toString())

            filters.forEach { (it as? UrlFilter)?.addToUrl(this) }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = Jsoup.parse(response.body.string(), response.request.url.toString())

        val mangas = document.select("a.manga-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.selectFirst(".card-title")?.text() ?: ""
                thumbnail_url = element.selectFirst(".cover img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("a[rel=next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = Jsoup.parse(response.body.string(), response.request.url.toString())

        return SManga.create().apply {
            title = document.selectFirst(".hero-title")?.text() ?: ""
            author = document.selectFirst(".meta-item-label:contains(Author) + .meta-item-value")?.text()
            artist = document.selectFirst(".meta-item-label:contains(Artist) + .meta-item-value")?.text()
            description = document.selectFirst(".synopsis-text")?.text()
            thumbnail_url = document.selectFirst(".hero-cover img")?.absUrl("src")

            status = when (document.selectFirst(".hero-status-badge")?.text()?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            val genres = document.select(".meta-pill-row .meta-pill").map { it.text() }.toMutableList()
            document.selectFirst(".meta-item-label:contains(Type) + .meta-item-value")?.text()?.let {
                if (it.isNotBlank()) genres.add(it)
            }
            genre = genres.joinToString()
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url + "?all=1", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = Jsoup.parse(response.body.string(), response.request.url.toString())

        return document.select(".chapter-row").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("data-href"))
                name = element.selectFirst(".chapter-title")?.text() ?: ""
                date_upload = element.selectFirst("[data-local-time]")?.attr("data-local-time")?.let {
                    dateFormat.tryParse(it)
                } ?: 0L
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request {
        if (!chapter.url.contains("/chapter/")) {
            // transform old Madara URLs
            OLD_URL_REGEX.find(chapter.url)?.let {
                val mangaSlug = it.groupValues[1]
                val number = it.groupValues[2].replace("-", ".")
                val formattedNumber = if (number.contains(".")) {
                    number.padEnd(number.indexOf(".") + 3, '0')
                } else {
                    "$number.00"
                }
                return GET("$baseUrl/manga/$mangaSlug/chapter/$formattedNumber", headers)
            }
        }

        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = Jsoup.parse(response.body.string(), response.request.url.toString())
        val script = document.selectFirst("script:containsData(const pages =)")?.data()
            ?: throw Exception("Script yang berisi data halaman tidak ditemukan")

        val pagesJson = PAGES_REGEX.find(script)?.groupValues?.get(1)
            ?: throw Exception("Data JSON halaman tidak ditemukan dalam script")

        val pages = pagesJson.parseAs<List<PageDto>>()

        return pages.map {
            Page(it.number - 1, "", it.url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList(): FilterList = FilterList(
        StatusFilter(),
        GenreFilter(),
        Filter.Separator(),
        OtherFilterGroup(),
    )

    @Serializable
    data class PageDto(
        val number: Int,
        val url: String,
    )

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        private val PAGES_REGEX = Regex("""const pages\s*=\s*(\[.*?\]);""", RegexOption.DOT_MATCHES_ALL)
        private val OLD_URL_REGEX = Regex("""/manga/([^/]+)/chapter-(\d+(?:-\d+)?)""")
    }
}
