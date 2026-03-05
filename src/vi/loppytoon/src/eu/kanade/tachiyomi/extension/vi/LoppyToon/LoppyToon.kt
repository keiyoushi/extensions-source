package eu.kanade.tachiyomi.extension.vi.loppytoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.TimeUnit

class LoppyToon : HttpSource() {
    override val name = "LoppyToon"
    override val lang = "vi"
    override val baseUrl = "https://loppytoon.com"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 20, 1, TimeUnit.MINUTES)
        .addNetworkInterceptor {
            val request = it.request()
            val response = it.proceed(request)

            if (request.url.toString().startsWith(baseUrl)) {
                if (response.code == 429) {
                    throw IOException("Bạn đang request quá nhanh!")
                }
            }
            response
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("div.hot-comic-item a.hot-comic-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("div.comic-title")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangaList, false)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/truyen-moi-cap-nhat?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("div.comic-item").map { element ->
            mangaFromElement(element)
        }

        val hasNextPage = document.selectFirst("i.fa-chevron-right[onclick]") != null

        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/api/search-story".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            return GET(url, headers)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val selected = filter.state.firstOrNull { it.state }
                    if (selected != null) {
                        return GET("$baseUrl/the-loai/${selected.slug}?page=$page", headers)
                    }
                }
                is GroupFilter -> {
                    val selected = filter.state.firstOrNull { it.state }
                    if (selected != null) {
                        return GET("$baseUrl/nhom-dich/${selected.slug}?page=$page", headers)
                    }
                }
                else -> {}
            }
        }

        return latestUpdatesRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url.toString()

        if (url.contains("/api/search-story")) {
            val results = response.parseAs<List<SearchResult>>()

            val mangaList = results.map { result ->
                SManga.create().apply {
                    setUrlWithoutDomain("/truyen/${result.slug}")
                    title = result.title ?: ""
                    thumbnail_url = result.cover?.let { cover ->
                        if (cover.startsWith("http")) cover else "$baseUrl/storage/$cover"
                    }
                }
            }

            return MangasPage(mangaList, false)
        }

        return latestUpdatesParse(response)
    }

    override fun getFilterList(): FilterList = getFilters()

    // =============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.manga-title")!!.text()

            author = document.selectFirst("span.meta-label:contains(Tác giả)")
                ?.nextElementSibling()?.text()

            genre = document.select(".manga-tags a.tag").joinToString { it.text() }

            thumbnail_url = document.selectFirst(".manga-cover img")?.absUrl("src")

            val altName = document.selectFirst("span.meta-label:contains(Tên khác)")
                ?.nextElementSibling()?.text()?.trim()

            val descElement = document.selectFirst("div.manga-description")
            val descText = descElement?.select("p")
                ?.filter { it.text().isNotBlank() }
                ?.joinToString("\n") { it.text() }?.trim()
                ?.ifEmpty { descElement.text().trim() }
                ?: ""

            description = if (!altName.isNullOrBlank()) {
                "Tên khác: $altName\n$descText"
            } else {
                descText
            }

            status = document.selectFirst("span.meta-label:contains(Tình trạng)")
                ?.nextElementSibling()?.text()?.trim()?.let { statusText ->
                    when {
                        statusText.contains("OnGoing", ignoreCase = true) ||
                            statusText.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
                        statusText.contains("Completed", ignoreCase = true) ||
                            statusText.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                } ?: SManga.UNKNOWN
        }
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("a")!!
        setUrlWithoutDomain(linkElement.absUrl("href"))
        title = element.selectFirst("h3.comic-title")!!.text()
        thumbnail_url = element.selectFirst(".comic-cover img")?.absUrl("src")
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val slug = response.request.url.pathSegments.last()

        val chapters = mutableListOf<SChapter>()

        // Parse initial chapters from the page
        chapters.addAll(parseChapters(document))

        // Load remaining chapters via AJAX
        var offset = chapters.size
        var hasMore = document.selectFirst("button.load-more-btn, .load-more") != null ||
            chapters.size >= 20

        while (hasMore) {
            val ajaxResponse = client.newCall(
                GET("$baseUrl/load-more-chapters?slug=$slug&offset=$offset&sortByPosition=desc", headers),
            ).execute()

            val chapterData = ajaxResponse.parseAs<ChapterResponse>()

            if (!chapterData.html.isNullOrBlank()) {
                val chapterDoc = Jsoup.parse(chapterData.html)
                val newChapters = parseChapters(chapterDoc)
                chapters.addAll(newChapters)
                offset += newChapters.size
            }

            hasMore = chapterData.has_more
        }

        return chapters
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("a.chapter-item").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst("h3")!!.text()
            date_upload = element.selectFirst("span.chapter-date")?.text().toDate()
        }
    }

    private fun String?.toDate(): Long {
        this ?: return 0L

        if (!this.contains("trước", ignoreCase = true)) {
            return 0L
        }

        return try {
            val calendar = Calendar.getInstance()

            val patterns = listOf(
                Regex("""(\d+)\s*giờ""", RegexOption.IGNORE_CASE) to Calendar.HOUR_OF_DAY,
                Regex("""(\d+)\s*ngày""", RegexOption.IGNORE_CASE) to Calendar.DAY_OF_MONTH,
                Regex("""(\d+)\s*tuần""", RegexOption.IGNORE_CASE) to Calendar.WEEK_OF_YEAR,
                Regex("""(\d+)\s*tháng""", RegexOption.IGNORE_CASE) to Calendar.MONTH,
                Regex("""(\d+)\s*năm""", RegexOption.IGNORE_CASE) to Calendar.YEAR,
                Regex("""(\d+)\s*phút""", RegexOption.IGNORE_CASE) to Calendar.MINUTE,
                Regex("""(\d+)\s*giây""", RegexOption.IGNORE_CASE) to Calendar.SECOND,
            )

            for ((pattern, field) in patterns) {
                pattern.find(this)?.groupValues?.get(1)?.toIntOrNull()?.let { number ->
                    calendar.add(field, -number)
                    return calendar.timeInMillis
                }
            }

            0L
        } catch (_: Exception) {
            0L
        }
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("img.manga-image").mapIndexed { index, element ->
            val imageUrl = element.absUrl("src")
                .ifEmpty { element.absUrl("data-src") }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Related ================================
    // dirty hack to disable suggested mangas on Komikku due to heavy rate limit
    // https://github.com/komikku-app/komikku/blob/4323fd5841b390213aa4c4af77e07ad42eb423fc/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt#L176-L184
    @Suppress("Unused")
    @JvmName("getDisableRelatedMangasBySearch")
    fun disableRelatedMangasBySearch() = true
}
