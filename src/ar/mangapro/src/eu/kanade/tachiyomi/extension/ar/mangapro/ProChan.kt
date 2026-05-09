package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import org.jsoup.nodes.Element

class ProComic : HttpSource() {

    override val name = "ProChan"

    override val lang = "ar"

    override val supportsLatest = true

    override val versionId = 2

    /*
     * =========================
     * تغيير رابط الموقع بسهولة
     * =========================
     */

    private var domain = "procomic.net"

    override val baseUrl: String
        get() = "https://$domain"

    /*
     * =========================
     * حماية ضد 403
     * =========================
     */

    override val client: OkHttpClient =
        network.cloudflareClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor(
                CookieInterceptor(
                    baseUrl.toHttpUrl(),
                    cookies,
                ),
            )
            .addInterceptor(
                Interceptor { chain ->

                    var request = chain.request()

                    val fixedUrl = request.url.toString()
                        .replace("prochan.net", domain)
                        .replace("www.prochan.net", domain)

                    request = request.newBuilder()
                        .url(fixedUrl)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                        )
                        .header("Referer", "$baseUrl/")
                        .header("Origin", baseUrl)
                        .header("Accept", "*/*")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .build()

                    chain.proceed(request)
                },
            )
            .build()

    override fun headersBuilder(): Headers.Builder =
        super.headersBuilder()
            .add(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            )
            .add("Referer", "$baseUrl/")
            .add("Origin", baseUrl)

    /*
     * =========================
     * Popular Manga
     * =========================
     */

    override fun popularMangaRequest(page: Int): Request {
        return GET(
            "$baseUrl/series?page=$page",
            headers,
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {

        val document = response.asJsoup()

        val mangas = document
            .select("a[href*=/series/]")
            .mapNotNull {
                mangaFromElement(it)
            }
            .distinctBy {
                it.url
            }

        val hasNextPage =
            document.select("a[rel=next]").isNotEmpty()

        return MangasPage(
            mangas,
            hasNextPage,
        )
    }

    /*
     * =========================
     * Latest Updates
     * =========================
     */

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(
            "$baseUrl/updates?page=$page",
            headers,
        )
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    /*
     * =========================
     * Search
     * =========================
     */

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {

        return GET(
            searchUrl(page, query, filters),
            headers,
        )
    }

    override fun searchMangaParse(
        response: Response,
    ): MangasPage {

        return popularMangaParse(response)
    }

    private fun searchUrl(
        page: Int,
        query: String,
        filters: FilterList,
    ): String {

        var status = ""
        var genre = ""
        var sort = ""

        filters.forEach { filter ->

            when (filter) {

                is StatusFilter -> {
                    status = filter.toUriPart()
                }

                is GenreFilter -> {
                    genre = filter.toUriPart()
                }

                is SortFilter -> {
                    sort = filter.toUriPart()
                }
            }
        }

        return buildString {

            append("$baseUrl/series?")

            if (query.isNotBlank()) {
                append("search=$query&")
            }

            if (status.isNotBlank()) {
                append("status=$status&")
            }

            if (genre.isNotBlank()) {
                append("genre=$genre&")
            }

            if (sort.isNotBlank()) {
                append("sort=$sort&")
            }

            append("page=$page")
        }
    }

    /*
     * =========================
     * Manga Details
     * =========================
     */

    override fun mangaDetailsParse(
        response: Response,
    ): SManga {

        val document = response.asJsoup()

        return SManga.create().apply {

            title =
                document.selectFirst("h1")
                    ?.text()
                    .orEmpty()

            author =
                document.selectFirst(".author")
                    ?.text()

            artist =
                document.selectFirst(".artist")
                    ?.text()

            genre =
                document.select(".genres a")
                    .joinToString(", ") {
                        it.text()
                    }

            description =
                document.selectFirst(
                    ".summary, .description",
                )?.text()

            thumbnail_url =
                document.selectFirst("img")
                    ?.absUrl("src")

            status = SManga.UNKNOWN
        }
    }

    /*
     * =========================
     * Chapters
     * =========================
     */

    override fun chapterListRequest(
        manga: SManga,
    ): Request {

        return GET(
            "$baseUrl${manga.url}",
            headers,
        )
    }

    override fun chapterListParse(
        response: Response,
    ): List<SChapter> {

        val document = response.asJsoup()

        return document
            .select("a[href*=/series/]")
            .mapNotNull {
                chapterFromElement(it)
            }
            .distinctBy {
                it.url
            }
    }

    private fun chapterFromElement(
        element: Element,
    ): SChapter? {

        val url =
            element.absUrl("href")

        if (!url.contains("/series/")) {
            return null
        }

        if (url == baseUrl || url.endsWith("/series")) {
            return null
        }

        return SChapter.create().apply {

            setUrlWithoutDomain(url)

            name =
                element.text()
                    .ifBlank {
                        "Chapter"
                    }

            date_upload = 0L
        }
    }

    /*
     * =========================
     * Pages
     * =========================
     */

    override fun pageListRequest(
        chapter: SChapter,
    ): Request {

        return GET(
            "$baseUrl${chapter.url}",
            headers,
        )
    }

    override fun pageListParse(
        response: Response,
    ): List<Page> {

        val document = response.asJsoup()

        val pages = mutableListOf<Page>()

        document.select("img").forEachIndexed { index, img ->

            val imageUrl =
                img.absUrl("src")

            if (
                imageUrl.contains(".jpg") ||
                imageUrl.contains(".jpeg") ||
                imageUrl.contains(".png") ||
                imageUrl.contains(".webp")
            ) {

                pages.add(
                    Page(
                        index,
                        "",
                        imageUrl,
                    ),
                )
            }
        }

        return pages.distinctBy {
            it.imageUrl
        }
    }

    override fun imageRequest(
        page: Page,
    ): Request {

        return GET(
            page.imageUrl!!,
            headers.newBuilder()
                .add("Referer", "$baseUrl/")
                .build(),
        )
    }

    /*
     * =========================
     * Manga Helper
     * =========================
     */

    private fun mangaFromElement(
        element: Element,
    ): SManga? {

        val url =
            element.absUrl("href")

        if (!url.contains("/series/")) {
            return null
        }

        return SManga.create().apply {

            title =
                element.selectFirst("img")
                    ?.attr("alt")
                    ?: element.text()

            thumbnail_url =
                element.selectFirst("img")
                    ?.absUrl("src")

            setUrlWithoutDomain(url)
        }
    }

    /*
     * =========================
     * Filters
     * =========================
     */

    override fun getFilterList(): FilterList {
        return FilterList(
            StatusFilter(),
            GenreFilter(),
            SortFilter(),
        )
    }

    /*
     * =========================
     * Status Filter
     * =========================
     */

    private class StatusFilter : UriPartFilter(
        "الحالة",
        arrayOf(
            Pair("الكل", ""),
            Pair("مستمرة", "ongoing"),
            Pair("مكتملة", "completed"),
            Pair("متوقفة", "hiatus"),
        ),
    )

    /*
     * =========================
     * Sort Filter
     * =========================
     */

    private class SortFilter : UriPartFilter(
        "الترتيب",
        arrayOf(
            Pair("الأحدث", "latest"),
            Pair("الأكثر مشاهدة", "popular"),
            Pair("الأعلى تقييماً", "rating"),
            Pair("الأبجدية", "title"),
        ),
    )

    /*
     * =========================
     * Genre Filter
     * =========================
     */

    private class GenreFilter : UriPartFilter(
        "التصنيف",
        arrayOf(

            Pair("الكل", ""),

            Pair("أكشن", "action"),
            Pair("مغامرة", "adventure"),
            Pair("كوميديا", "comedy"),
            Pair("دراما", "drama"),
            Pair("خيال", "fantasy"),
            Pair("سحر", "magic"),
            Pair("وحوش", "monsters"),
            Pair("زنزانة", "dungeon"),
            Pair("رعب", "horror"),
            Pair("قتال", "martial-arts"),
            Pair("مدرسي", "school"),
            Pair("رومانسي", "romance"),
            Pair("شونين", "shounen"),
            Pair("سينين", "seinen"),
            Pair("غموض", "mystery"),
            Pair("نفسي", "psychological"),
            Pair("إيسيكاي", "isekai"),
            Pair("إثارة", "thriller"),
            Pair("تاريخي", "historical"),
            Pair("خارق للطبيعة", "supernatural"),
            Pair("انتقام", "revenge"),
            Pair("بقاء", "survival"),
            Pair("ويب تون", "webtoon"),
            Pair("رياضة", "sports"),
            Pair("خيال علمي", "sci-fi"),
            Pair("شياطين", "demons"),
            Pair("مصاصي دماء", "vampire"),
            Pair("إعادة حياة", "reincarnation"),
            Pair("نظام", "system"),
            Pair("طبخ", "cooking"),
            Pair("موسيقى", "music"),
            Pair("عسكري", "military"),
        ),
    )

    /*
     * =========================
     * Base Filter
     * =========================
     */

    open class UriPartFilter(
        name: String,
        private val vals: Array<Pair<String, String>>,
    ) : Filter.Select<String>(
        name,
        vals.map {
            it.first
        }.toTypedArray(),
    ) {

        fun toUriPart(): String {
            return vals[state].second
        }
    }
}
