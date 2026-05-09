package eu.kanade.tachiyomi.extension.ar.mangapro

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt

class ProComic : HttpSource() {

    override val name = "ProChan"
    override val lang = "ar"
    override val supportsLatest = true
    override val versionId = 2

    // =========================
    // Settings (Domain Switch)
    // =========================

    private val prefs: SharedPreferences by lazy {
        Injekt.get<Application>()
            .getSharedPreferences("procomic_prefs", 0)
    }

    private val defaultDomains = listOf(
        "procomic.net",
        "www.procomic.net"
    )

    private val domains: List<String>
        get() {
            val saved = prefs.getString("domains", "") ?: ""
            return if (saved.isBlank()) defaultDomains
            else saved.split(",").map { it.trim() }
        }

    private val domain: String
        get() = domains.first()

    override val baseUrl: String
        get() = "https://$domain"

    // =========================
    // Client (Stable + Safe)
    // =========================

    override val client: OkHttpClient =
        network.cloudflareClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .addInterceptor(
                CookieInterceptor(
                    baseUrl.toHttpUrl(),
                    cookies,
                )
            )
            .retryOnConnectionFailure(true)
            .build()

    // =========================
    // Popular
    // =========================

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/series?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {

        val doc = response.asJsoup()

        val mangas = doc.select("a[href*=/series/]")
            .mapNotNull { mangaFromElement(it) }
            .distinctBy { it.url }

        val hasNext = doc.select("a[rel=next]").isNotEmpty()

        return MangasPage(mangas, hasNext)
    }

    // =========================
    // Latest
    // =========================

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/updates?page=$page", headers)

    override fun latestUpdatesParse(response: Response) =
        popularMangaParse(response)

    // =========================
    // Search
    // =========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET(buildSearchUrl(page, query, filters), headers)
    }

    override fun searchMangaParse(response: Response) =
        popularMangaParse(response)

    private fun buildSearchUrl(page: Int, query: String, filters: FilterList): String {

        var status = ""
        var genre = ""
        var sort = ""

        filters.forEach {
            when (it) {
                is StatusFilter -> status = it.toUriPart()
                is GenreFilter -> genre = it.toUriPart()
                is SortFilter -> sort = it.toUriPart()
            }
        }

        return buildString {
            append("$baseUrl/series?")
            if (query.isNotBlank()) append("search=$query&")
            if (status.isNotBlank()) append("status=$status&")
            if (genre.isNotBlank()) append("genre=$genre&")
            if (sort.isNotBlank()) append("sort=$sort&")
            append("page=$page")
        }
    }

    // =========================
    // Details
    // =========================

    override fun mangaDetailsParse(response: Response): SManga {

        val doc = response.asJsoup()

        return SManga.create().apply {
            title = doc.selectFirst("h1")?.text().orEmpty()
            author = doc.selectFirst(".author")?.text()
            artist = doc.selectFirst(".artist")?.text()

            genre = doc.select(".genres a")
                .joinToString(", ") { it.text() }

            description = doc.selectFirst(".summary, .description")?.text()

            thumbnail_url = doc.selectFirst("img")?.absUrl("src")

            status = SManga.UNKNOWN
        }
    }

    // =========================
    // Chapters
    // =========================

    override fun chapterListRequest(manga: SManga) =
        GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {

        val doc = response.asJsoup()

        return doc.select("a[href*=/chapter], a[href*=/series/]")
            .mapNotNull { chapterFromElement(it) }
            .distinctBy { it.url }
    }

    private fun chapterFromElement(element: Element): SChapter? {

        val url = element.absUrl("href")

        if (!url.contains("/chapter") && !url.contains("/series/")) return null
        if (url == baseUrl) return null

        if (isPaidChapter(element)) return null

        return SChapter.create().apply {
            setUrlWithoutDomain(url)
            name = element.text().ifBlank { "Chapter" }
            date_upload = 0L
        }
    }

    // =========================
    // Smart paywall detection
    // =========================

    private fun isPaidChapter(element: Element): Boolean {

        val text = element.text().lowercase()

        return element.select(
            ".lock, .premium, .vip, .locked, i[class*=lock]"
        ).isNotEmpty() ||
                element.attr("data-premium") == "true" ||
                element.attr("data-lock") == "1" ||
                element.attr("data-paywall") == "true" ||
                element.attr("aria-label").contains("lock", true) ||
                text.contains("premium") ||
                text.contains("vip") ||
                text.contains("locked") ||
                text.contains("مدفوع") ||
                text.contains("شراء")
    }

    // =========================
    // Pages
    // =========================

    override fun pageListRequest(chapter: SChapter) =
        GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {

        val doc = response.asJsoup()

        return doc.select("main img, .reader-area img, .chapter-content img")
            .mapIndexedNotNull { index, img ->

                val url = img.absUrl("src")

                val validImage =
                    url.matches(Regex(""".*\.(jpg|jpeg|png|webp)(\?.*)?$"""))

                val isAd = img.className().contains("ad", true)

                if (validImage && !isAd) {
                    Page(index, "", url)
                } else null
            }
            .distinctBy { it.imageUrl }
    }

    override fun imageRequest(page: Page) =
        GET(
            page.imageUrl!!,
            headers.newBuilder()
                .add("Referer", "$baseUrl/")
                .build()
        )

    // =========================
    // Helper
    // =========================

    private fun mangaFromElement(element: Element): SManga? {

        val url = element.absUrl("href")
        if (!url.contains("/series/")) return null

        return SManga.create().apply {
            title = element.selectFirst("img")?.attr("alt") ?: element.text()
            thumbnail_url = element.selectFirst("img")?.absUrl("src")
            setUrlWithoutDomain(url)
        }
    }

    // =========================
    // Filters
    // =========================

    override fun getFilterList() = FilterList(
        StatusFilter(),
        GenreFilter(),
        SortFilter()
    )

    private class StatusFilter : UriPartFilter(
        "الحالة",
        arrayOf(
            Pair("الكل", ""),
            Pair("مستمرة", "ongoing"),
            Pair("مكتملة", "completed"),
            Pair("متوقفة", "hiatus"),
        )
    )

    private class SortFilter : UriPartFilter(
        "الترتيب",
        arrayOf(
            Pair("الأحدث", "latest"),
            Pair("الأكثر مشاهدة", "popular"),
            Pair("الأعلى تقييماً", "rating"),
            Pair("الأبجدية", "title"),
        )
    )

    private class GenreFilter : UriPartFilter(
        "التصنيف",
        arrayOf(
            Pair("الكل", ""),
            Pair("أكشن", "action"),
            Pair("مغامرة", "adventure"),
            Pair("كوميديا", "comedy"),
            Pair("دراما", "drama"),
            Pair("خيال علمي", "sci-fi"),
            Pair("إيسيكاي", "isekai"),
            Pair("رومانسي", "romance"),
            Pair("رعب", "horror"),
            Pair("موسيقى", "music"),
            Pair("عسكري", "military"),
        )
    )

    open class UriPartFilter(
        name: String,
        private val vals: Array<Pair<String, String>>,
    ) : Filter.Select<String>(
        name,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toUriPart() = vals[state].second
    }
}
