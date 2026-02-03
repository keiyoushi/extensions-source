package eu.kanade.tachiyomi.extension.en.azuki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Azuki : HttpSource() {

    override val name = "Azuki"
    override val baseUrl = "https://www.azuki.co"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "https://production.api.azuki.co"
    private val organizationKey = "199e5a19-a236-49f5-81f4-43d4a541748a"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .rateLimitHost(apiUrl.toHttpUrl(), 1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/discover?sort=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ol.o-series-card-list li").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/discover?sort=recent_series&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/discover".toHttpUrl().newBuilder()
        url.addQueryParameter("q", query)
        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.toUriPart())

                is AccessTypeFilter -> filter.toUriPart().takeIf { it.isNotEmpty() }?.let { url.addQueryParameter("access_type", it) }

                is GenreFilter ->
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("tags[]", it.value) }

                is PublisherFilter -> filter.toUriPart().takeIf { it.isNotEmpty() }?.let { url.addQueryParameter("publisher_slug", it) }

                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            author = document.selectFirst(".o-series-summary__byline p")?.text()?.substringAfter("By ")?.substringBefore(" Published by")
            artist = author
            description = document.selectFirst(".o-series-summary__description")?.text()
            genre = document.select(".o-series-summary__genres a").joinToString { it.text() }
            thumbnail_url = document.selectFirst(".o-series-summary__cover img")?.absUrl("src")
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val seriesSlug = response.request.url.pathSegments.lastOrNull() ?: return emptyList()
        val mangaUuid = document.selectFirst("azuki-chapter-row-list[series-uuid]")?.attr("series-uuid")

        val unlockedChapterIds = if (mangaUuid != null) {
            try {
                val apiResponse = client.newCall(GET("$apiUrl/user/mangas/$mangaUuid/v0", apiHeaders())).execute()
                if (apiResponse.isSuccessful) {
                    val result = apiResponse.parseAs<UserMangaStatusDto>()
                    (result.purchasedChapterUuids + result.unlockedChapterUuids).toSet()
                } else {
                    emptySet()
                }
            } catch (e: Exception) {
                emptySet()
            }
        } else {
            emptySet()
        }

        return document.select(".m-chapter-row-list .m-chapter-row").mapNotNull { chapterRow ->
            val link = chapterRow.selectFirst("a.a-card-link") ?: return@mapNotNull null
            val href = link.absUrl("href").toHttpUrl()

            val chapterId = if ("/checkout/" in href.encodedPath) {
                href.queryParameter("chapter_uuids[]")
            } else {
                href.pathSegments.lastOrNull()
            }

            if (chapterId.isNullOrEmpty()) return@mapNotNull null

            SChapter.create().apply {
                url = "/series/$seriesSlug/read/$chapterId"
                name = link.selectFirst(".m-chapter-row__title-cluster span")?.text() ?: link.text()
                date_upload = dateFormat.tryParse(chapterRow.selectFirst(".m-chapter-row__date time")?.attr("datetime"))

                val isPremium = chapterRow.selectFirst(".m-chapter-row__premium-badge") != null ||
                    chapterRow.parent()?.hasClass("m-chapter-card--secondary") == true

                if (isPremium && chapterId !in unlockedChapterIds) {
                    name = "🔒 $name"
                }
            }
        }.reversed()
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfter("/read/")
        val apiUrl = "$apiUrl/chapters/$chapterId/pages/v1"
        return GET(apiUrl, apiHeaders())
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = client.newCall(pageListRequest(chapter))
        .asObservable()
        .map { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) {
                    throw Exception("This chapter is locked. Log in via WebView and unlock the chapter to read.")
                }
                throw Exception("HTTP error ${response.code}")
            }
            pageListParse(response)
        }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PageListDto>()
        return result.data.pages.mapIndexed { i, page ->
            val imageList = page.image.webp ?: page.image.jpg
                ?: throw Exception("No images found for page ${i + 1}")

            val bestAvailableUrl = imageList.maxByOrNull { it.width }?.url
                ?: throw Exception("No image URL found for page ${i + 1}")

            val resolutionRegex = Regex("""/(\d+)\.(webp|jpg)$""")
            val highResUrl = resolutionRegex.replace(bestAvailableUrl, "/2000.$2")

            Page(i, imageUrl = "$highResUrl?drm=1")
        }
    }

    private fun apiHeaders(): Headers {
        val token = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == "idToken" }?.value

        return headersBuilder()
            .set("azuki-organization-key", organizationKey)
            .apply {
                if (token != null) {
                    set("x-user-token", token)
                }
            }
            .build()
    }

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        AccessTypeFilter(),
        PublisherFilter(),
        GenreFilter(),
    )

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a.a-card-link")!!
        setUrlWithoutDomain(link.attr("href"))
        title = link.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // Unsupported
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
