package eu.kanade.tachiyomi.extension.fr.mangakawaii

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Heavily customized MyMangaReaderCMS source
 */
class MangaKawaii : ParsedHttpSource() {

    override val name = "Mangakawaii"
    override val baseUrl = "https://www.mangakawaii.io"
    private val cdnUrl = "https://cdn.mangakawaii.io"
    override val lang = "fr"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(2)
        .build()

    private val userAgentRandomizer1 = "${Random.nextInt(9).absoluteValue}"
    private val userAgentRandomizer2 = "${Random.nextInt(10, 99).absoluteValue}"
    private val userAgentRandomizer3 = "${Random.nextInt(100, 999).absoluteValue}"

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/8$userAgentRandomizer1.0.4$userAgentRandomizer3.1$userAgentRandomizer2 Safari/537.36",
        )
        .add(
            "Accept-Language",
            lang,
        )

    // Popular
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)
    override fun popularMangaSelector() = "a.hot-manga__item"
    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.hot-manga__item-caption").select("div.hot-manga__item-name").text().trim()
        setUrlWithoutDomain(element.select("a").attr("href"))
        thumbnail_url = "$cdnUrl/uploads" + element.select("a").attr("href") + "/cover/cover_250x350.jpg"
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)
    override fun latestUpdatesSelector() = ".section__list-group li div.section__list-group-left"
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a").attr("title")
        setUrlWithoutDomain(element.select("a").attr("href"))
        thumbnail_url = "$cdnUrl/uploads" + element.select("a").attr("href") + "/cover/cover_250x350.jpg"
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/search").buildUpon()
            .appendQueryParameter("query", query)
            .appendQueryParameter("search_type", "manga")
            .appendQueryParameter("page", page.toString())
        return GET(uri.toString(), headers)
    }
    override fun searchMangaSelector() = "div.section__list-group-heading"
    override fun searchMangaNextPageSelector(): String = "ul.pagination a[rel*=next]"
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a").text().trim()
        setUrlWithoutDomain(element.select("a").attr("href"))
        thumbnail_url = "$cdnUrl/uploads" + element.select("a").attr("href") + "/cover/cover_250x350.jpg"
    }

    // Manga details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.select("div.manga-view__header-image").select("img").attr("abs:src")
        description = document.select("dd.text-justify.text-break").text()
        author = document.select("a[href*=author]").text()
        artist = document.select("a[href*=artist]").text()
        genre = document.select("a[href*=category]").joinToString { it.text() }
        status = when (document.select("span.badge.bg-success.text-uppercase").text()) {
            "En Cours" -> SManga.ONGOING
            "Terminé" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        // add alternative name to manga description
        document.select("span[itemprop=name alternativeHeadline]").joinToString { it.ownText() }.let {
            if (it.isNotBlank()) {
                description = when {
                    description.isNullOrBlank() -> "Alternative Names: $it"
                    else -> "$description\n\nAlternative Names: $it"
                }
            }
        }
    }

    // Chapter list
    override fun chapterListSelector() = throw Exception("Not used")
    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val visibleChapters = document.select("tr[class*='volume-']")
        if (!visibleChapters.isEmpty()) {
            // There is chapters, but the complete list isn't always displayed here
            // To get the whole list, let's instead go to a manga page to get the list of links
            val someChapter = visibleChapters[0].select(".table__chapter > a").attr("href")
            val mangaDocument = client.newCall(GET("$baseUrl$someChapter", headers)).execute().asJsoup()
            val notVisibleChapters = mangaDocument.select("#dropdownMenuOffset+ul li")

            // If not everything is displayed
            if (visibleChapters.count() < notVisibleChapters.count()) {
                return notVisibleChapters.map {
                    SChapter.create().apply {
                        setUrlWithoutDomain(it.select("a").attr("href"))
                        name = it.select("a").text()
                        date_upload = today
                    }
                }
            } else {
                return visibleChapters.map {
                    SChapter.create().apply {
                        setUrlWithoutDomain(it.select("td.table__chapter > a").attr("href"))
                        name = it.select("td.table__chapter > a span").text()
                        date_upload = parseDate(it.select("td.table__date").text())
                    }
                }
            }
        }
        return mutableListOf()
    }

    private val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(date)?.time ?: today
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val chapterSlug = Regex("""var chapter_slug = "([^"]*)";""").find(document.toString())?.groupValues?.get(1)
        val mangaSlug = Regex("""var oeuvre_slug = "([^"]*)";""").find(document.toString())?.groupValues?.get(1)

        val pages = mutableListOf<Page>()
        Regex(""""page_image":"([^"]*)"""").findAll(document.toString()).asIterable().mapIndexed { i, it ->
            pages.add(
                Page(
                    i,
                    cdnUrl + "/uploads/manga/" + mangaSlug + "/chapters_fr/" + chapterSlug + "/" + it.groupValues[1],
                    cdnUrl + "/uploads/manga/" + mangaSlug + "/chapters_fr/" + chapterSlug + "/" + it.groupValues[1],
                ),
            )
        }
        return pages
    }
    override fun imageUrlParse(document: Document): String = throw Exception("Not used")
    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeaders)
    }
}
