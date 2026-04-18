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
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

// Madara to HttpSource
class TheManga : HttpSource() {

    override val name = "TheManga"
    override val baseUrl = "https://themanga.my.id"
    override val lang = "id"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    // =============================== Popular ================================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/?q=&sort=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // =============================== Latest =================================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?q=&sort=latest_update&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // =============================== Search =================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            addQueryParameter("page", page.toString())

            filters.forEach { (it as? UrlFilter)?.addToUrl(this) }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("a.card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.selectFirst(".card-title")!!.text()
                thumbnail_url = element.selectFirst(".card-cover img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst(".explore-pagination__btn[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".hero-title")!!.text()
            author = document.meta("Author")
            artist = document.meta("Artist")
            description = document.selectFirst(".synopsis-text")?.text()
            thumbnail_url = document.selectFirst(".hero-cover img")?.absUrl("src")

            status = when (document.selectFirst(".hero-status-badge")?.text()?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            val genres = document.select(".meta-pill-row .meta-pill")
                .map { it.text() }
                .toMutableList()

            document.meta("Type")?.takeIf { it.isNotBlank() }?.let(genres::add)

            genre = genres.joinToString()
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}?all=1", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".chapter-row").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("data-href"))
                name = element.selectFirst(".chapter-title")!!.text()
                date_upload = dateFormat.tryParse(element.selectFirst("[data-local-time]")?.attr("data-local-time"))
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request {
        if (!chapter.url.contains("/chapter/")) {
            // Support old Madara URLs
            val segments = chapter.url.toHttpUrl().pathSegments
            val mangaSlug = segments[1]
            val number = segments[2].removePrefix("chapter-").replace("-", ".")
            val dotIndex = number.indexOf('.')
            val formatted = if (dotIndex >= 0) {
                number.padEnd(dotIndex + 3, '0')
            } else {
                "$number.00"
            }

            return GET("$baseUrl/manga/$mangaSlug/chapter/$formatted", headers)
        }

        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("img.page-img").mapIndexed { idx, image ->
            Page(idx, imageUrl = image.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(),
        Filter.Separator(),
        OtherFilterGroup(),
    )

    // ============================== Utils ===============================
    private fun Document.meta(label: String): String? = selectFirst(".meta-item-label:matchesOwn(^$label$) + .meta-item-value")?.text()

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT)
    }
}
