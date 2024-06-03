package eu.kanade.tachiyomi.extension.es.ladroncorps

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LadronCorps : HttpSource() {
    override val name: String = "Ladron Corps"

    override val baseUrl: String = "https://www.ladroncorps.com"

    override val lang: String = "es"

    override val supportsLatest: Boolean = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    private val json by injectLazy<Json>()

    private val authorization: String by lazy {
        val response = client.newCall(GET("$baseUrl/_api/v2/dynamicmodel", headers)).execute()
        val authDto = response.parseAs<AuthDto>()
        authDto.randomToken()
    }

    private val apiHeaders: Headers by lazy {
        headers.newBuilder()
            .set("Authorization", authorization)
            .build()
    }

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage {
        val posts = response.parseAs<PopularMangaContainerDto>().posts

        val mangas = posts.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = "${it.cover.url}"
                url = "${it.url}"
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/blog-frontend-adapter-public/v2/post-feed-page".toHttpUrl().newBuilder()
            .addQueryParameter("includeContent", "false")
            .addQueryParameter("languageCode", lang)
            .addQueryParameter("page", "$page")
            .addQueryParameter("pageSize", "20")
            .addQueryParameter("type", "ALL_POSTS")
            .build()
        return GET(url, apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val posts = response.parseAs<SearchDto>().posts

        val mangas = posts.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = it.cover.url
                url = it.url
            }
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/_api/communities-blog-node-api/_api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        return GET(url, apiHeaders)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val manga = SManga.create().apply {
                url = "/post/${query.substringAfter(URL_SEARCH_PREFIX)}"
            }
            return fetchMangaDetails(manga).asObservable().map {
                MangasPage(listOf(it), false)
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        description = document.select("div[data-hook='post-description'] p > span")
            .joinToString("\n".repeat(2)) { it.text() }

        genre = document.select("#post-footer li a")
            .joinToString { it.text() }

        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE

        status = SManga.COMPLETED

        setUrlWithoutDomain(document.location())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                name = "Capitulo único"
                date_upload = parseDate(document.selectFirst("span[data-hook='time-ago']")?.text() ?: "")
                setUrlWithoutDomain(document.location())
            },
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val selectors = "figure[data-hook='imageViewer'] img, img[data-hook='gallery-item-image-img']"
        return document.select(selectors).mapIndexed { index, element ->
            Page(index, document.location(), imageUrl = element.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    private fun Element.imgAttr(): String = when {
        hasAttr("data-pin-media") -> absUrl("data-pin-media")
        else -> absUrl("src")
    }

    private fun parseDate(date: String): Long =
        try { dateFormat.parse(dateSanitize(date))!!.time } catch (_: Exception) { parseRelativeDate(date) }

    private fun parseRelativeDate(date: String): Long {
        val number = RELATIVE_DATE_REGEX.find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()
        return when {
            date.contains("día", ignoreCase = true) -> cal.apply { add(Calendar.DATE, -number) }.timeInMillis
            date.contains("mes", ignoreCase = true) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            date.contains("año", ignoreCase = true) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }

    private fun dateSanitize(date: String): String =
        if (D_MMM_REGEX.matches(date)) "$date ${Calendar.getInstance().get(Calendar.YEAR)}" else date

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(body.string())
    }

    companion object {
        const val URL_SEARCH_PREFIX = "slug:"

        val RELATIVE_DATE_REGEX = """(\d+)""".toRegex()
        val D_MMM_REGEX = """\d+ \w+$""".toRegex()

        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale("es"))
    }
}
