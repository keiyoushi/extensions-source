package eu.kanade.tachiyomi.extension.ru.mangahub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.random.Random

open class Mangahub : ParsedHttpSource() {

    override val name = "Mangahub"

    override val baseUrl = "https://mangahub.ru"

    override val lang = "ru"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(::confirmAgeInterceptor)
        .rateLimit(2)
        .build()

    private fun confirmAgeInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.method != "GET" ||
            response.header("Content-Type")?.contains("text/html") != true
        ) {
            return response
        }

        val document = Jsoup.parse(
            response.peekBody(Long.MAX_VALUE).string(),
            request.url.toString(),
        )

        val formElement = document.selectFirst("#confirm_age__token")
            ?: return response

        val formBody = FormBody.Builder()
            .addEncoded(formElement.attr("name"), formElement.attr("value"))
            .build()

        val confirmAgeRequest = request.newBuilder()
            .method("POST", formBody)
            .build()

        return client.newCall(confirmAgeRequest).execute()
    }

    private val userAgentRandomizer = "${Random.nextInt().absoluteValue}"

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36 Edg/100.0.$userAgentRandomizer")
        add("Referer", baseUrl)
    }

    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page > 1) "?page=$page" else ""
        return GET("$baseUrl/explore/sort-is-rating$pageStr", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val pageStr = if (page > 1) "?page=$page" else ""
        return GET("$baseUrl/explore/sort-is-update$pageStr", headers)
    }

    override fun popularMangaSelector() = "div.item-grid"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            thumbnail_url = element.selectFirst("img.item-grid-image")?.absUrl("src")
            title = element.selectFirst("a.fw-medium")!!.text()
            setUrlWithoutDomain(element.selectFirst("a.fw-medium")!!.absUrl("href"))
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = ".page-link:contains(→)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/title".toHttpUrl().newBuilder().apply {
            addQueryParameter("query", query)
            if (page > 1) addQueryParameter("page", page.toString())
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + ("/chapters/" + manga.url.removePrefix("/title/")), headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            val authorElement = document.selectFirst(".attr-name:contains(Автор) + .attr-value a")
            if (authorElement != null) {
                author = authorElement.text()
            } else {
                author = document.selectFirst(".attr-name:contains(Сценарист) + .attr-value a")?.text()
                artist = document.selectFirst(".attr-name:contains(Художник) + .attr-value a")?.text()
            }
            genre = document.select(".tags a").joinToString { it.text() }
            description = document.selectFirst(".markdown-style.text-expandable-content")?.text()
            val statusElement = document.selectFirst(".attr-name:contains(Томов) + .attr-value")?.text()
            status = when {
                statusElement?.contains("продолжается") == true -> SManga.ONGOING
                statusElement?.contains("приостановлен") == true -> SManga.ON_HIATUS
                statusElement?.contains("завершен") == true || statusElement?.contains("выпуск прекращён") == true ->
                    if (document.selectFirst(".attr-name:contains(Перевод) + .attr-value")?.text()?.contains("Завершен") == true) {
                        SManga.COMPLETED
                    } else {
                        SManga.PUBLISHING_FINISHED
                    }
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst("img.cover-detail")?.absUrl("src")
        }
    }

    override fun chapterListSelector() = "div.py-2.px-3"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("div.align-items-center > a").first()!!
        val chapter = SChapter.create()
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("div.text-muted").text().let {
            SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(it)?.time ?: 0L
        }
        chapter.setUrlWithoutDomain(urlElement.absUrl("href"))
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("(Глава\\s)((\\d|\\.)+)")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[2]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val images = document.select("img.reader-viewer-img")
        return images.mapIndexed { i, img ->
            val url = img.attr("data-src").let { if (it.startsWith("//")) "https:$it" else it }
            Page(i, document.location(), url)
        }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
            .add("Referer", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeader)
    }
}
