package eu.kanade.tachiyomi.extension.en.mangatop

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.lang.Exception
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MangaScans : ParsedHttpSource() {

    override val name = "MangaScans"

    override val baseUrl = "https://mangascans.to"

    override val lang = "en"

    override val supportsLatest = true

    override val id = 85127596998931837

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::tokenInterceptor)
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

    private var storedToken: String? = null

    // From Akuma
    private fun tokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.method == "POST" && request.header("X-CSRF-TOKEN") == null) {
            val newRequest = request.newBuilder()
            val token = getToken()
            val response = chain.proceed(
                newRequest
                    .addHeader("X-CSRF-TOKEN", token)
                    .build(),
            )

            if (response.code == 419) {
                response.close()
                storedToken = null // reset the token
                val newToken = getToken()
                return chain.proceed(
                    newRequest
                        .addHeader("X-CSRF-TOKEN", newToken)
                        .build(),
                )
            }

            return response
        }

        return chain.proceed(request)
    }

    private fun getToken(): String {
        if (storedToken.isNullOrEmpty()) {
            val request = GET(baseUrl, headers)
            val response = client.newCall(request).execute()

            val document = response.asJsoup()
            document.updateToken()
        }

        return storedToken!!
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        document.updateToken()

        val mangaList = document.select(popularMangaSelector())
            .map(::popularMangaFromElement)

        return MangasPage(mangaList, false)
    }

    override fun popularMangaSelector(): String = "aside div > article"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")!!.imgAttr()
        with(element.selectFirst("a:has(h3)")!!) {
            setUrlWithoutDomain(attr("abs:href"))
            title = text()
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        document.updateToken()

        val mangaList = document.select(latestUpdatesSelector())
            .map(::latestUpdatesFromElement)

        val hasNextPage = document.selectFirst(latestUpdatesNextPageSelector()) != null

        return MangasPage(mangaList, hasNextPage)
    }

    override fun latestUpdatesSelector(): String = "div > article.manga-item"

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = "ul.pagination > li.active + li:has(a)"

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            filterList.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage =
        latestUpdatesParse(response)

    override fun searchMangaSelector(): String =
        throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element): SManga =
        throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector(): String =
        throw UnsupportedOperationException()

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        TypeFilter(),
        GenreFilter(),
        StatusFilter(),
    )

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.selectFirst("picture img")!!.imgAttr()
        with(document.selectFirst(".manga-info")!!) {
            title = selectFirst("h1.page-heading")!!.text()
            author = selectFirst("ul > li:has(span:contains(Authors))")?.ownText()
            genre = select("ul > li:has(span:contains(Genres)) a").joinToString { it.text() }
            status = selectFirst(".text-info").parseStatus()
            description = selectFirst("#manga-description")?.text()
                ?.split(".")
                ?.filterNot { it.contains("MangaTop") }
                ?.joinToString(".")
                ?.trim()
        }
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        document.updateToken()

        val mangaName = document.selectFirst("script:containsData(mangaName)")
            ?.data()
            ?.substringAfter("mangaName")
            ?.substringAfter("'")
            ?.substringBefore("'")
            ?: throw Exception("Failed to get form data")

        val postHeaders = apiHeadersBuilder().apply {
            set("Referer", response.request.url.toString())
        }.build()

        val postBody = FormBody.Builder().apply {
            add("mangaIdx", response.request.url.toString().substringAfterLast("-"))
            add("mangaName", mangaName)
        }.build()

        val postResponse = client.newCall(
            POST("$baseUrl/chapter-list", postHeaders, postBody),
        ).execute()

        return super.chapterListParse(postResponse)
    }

    override fun chapterListSelector() = "li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst(".text-muted")?.also {
            date_upload = it.text().parseDate()
        }
        name = element.selectFirst("span:not(.text-muted)")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
    }

    private fun String.parseDate(): Long {
        return if (this.contains("ago")) {
            this.parseRelativeDate()
        } else {
            try {
                dateFormat.parse(this)!!.time
            } catch (_: ParseException) {
                0L
            }
        }
    }

    private fun String.parseRelativeDate(): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val relativeDate = this.split(" ").firstOrNull()
            ?.toIntOrNull()
            ?: return 0L

        when {
            "second" in this -> now.add(Calendar.SECOND, -relativeDate) // parse: 30 seconds ago
            "minute" in this -> now.add(Calendar.MINUTE, -relativeDate) // parses: "42 minutes ago"
            "hour" in this -> now.add(Calendar.HOUR, -relativeDate) // parses: "1 hour ago" and "2 hours ago"
            "day" in this -> now.add(Calendar.DAY_OF_YEAR, -relativeDate) // parses: "2 days ago"
            "week" in this -> now.add(Calendar.WEEK_OF_YEAR, -relativeDate) // parses: "2 weeks ago"
            "month" in this -> now.add(Calendar.MONTH, -relativeDate) // parses: "2 months ago"
            "year" in this -> now.add(Calendar.YEAR, -relativeDate) // parse: "2 years ago"
        }
        return now.timeInMillis
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringBeforeLast(".html")
            .substringAfterLast("-")

        val postHeaders = apiHeadersBuilder().apply {
            set("Referer", baseUrl + chapter.url)
        }.build()

        val postBody = FormBody.Builder().apply {
            add("chapterIdx", chapterId)
        }.build()

        return POST("$baseUrl/chapter-resources", postHeaders, postBody)
    }

    @Serializable
    class PageListResponse(
        val data: PageListDataDto,
    ) {
        @Serializable
        class PageListDataDto(
            val resources: List<PageDto>,
        ) {
            @Serializable
            class PageDto(
                val name: Int,
                val thumb: String,
            )
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<PageListResponse>().data.resources.map {
            Page(it.name, imageUrl = it.thumb)
        }
    }

    override fun pageListParse(document: Document): List<Page> =
        throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    // ============================= Utilities ==============================

    private fun Document.updateToken() {
        storedToken = this.selectFirst("head meta[name*=csrf-token]")
            ?.attr("content")
            ?: throw IOException("Failed to update token")
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }

    private fun apiHeadersBuilder() = headersBuilder().apply {
        add("Accept", "*/*")
        add("Host", baseUrl.toHttpUrl().host)
        add("Origin", baseUrl)
        add("X-Requested-With", "XMLHttpRequest")
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }
}
