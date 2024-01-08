package eu.kanade.tachiyomi.extension.fr.furyosquad

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class FuryoSquad : ParsedHttpSource() {

    override val name = "FuryoSquad"

    override val baseUrl = "https://www.furyosociety.com/"

    override val lang = "fr"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(1)
        .build()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mangas", headers)
    }

    override fun popularMangaSelector() = "div#fs-tous div.fs-card-body"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            manga.url = select("div.fs-card-img-container a").attr("href")
            manga.title = select("span.fs-comic-title a").text()

            manga.thumbnail_url = select("div.fs-card-img-container img").attr("abs:src")
        }

        return manga
    }

    override fun popularMangaNextPageSelector() = "Not needed"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()

        document.select(latestUpdatesSelector()).map { mangas.add(latestUpdatesFromElement(it)) }

        return MangasPage(mangas.distinctBy { it.url }, false)
    }

    override fun latestUpdatesSelector() = "table.table-striped tr"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            manga.url = select("span.fs-comic-title a").attr("href")
            manga.title = select("span.fs-comic-title a").text()

            manga.thumbnail_url = select("img.fs-chap-img").attr("abs:src")
        }

        return manga
    }

    override fun latestUpdatesNextPageSelector() = "not needed"

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        return MangasPage(popularMangaParse(response).mangas.filter { it.title.contains(query, ignoreCase = true) }, false)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        document.select("div.comic-info").let {
            it.select("p.fs-comic-label").forEach { el ->
                when (el.text().lowercase(Locale.ROOT)) {
                    "scénario" -> manga.author = el.nextElementSibling()!!.text()
                    "dessins" -> manga.artist = el.nextElementSibling()!!.text()
                    "genre" -> manga.genre = el.nextElementSibling()!!.text()
                }
            }
            manga.description = it.select("div.fs-comic-description").text()
            manga.thumbnail_url = it.select("img.comic-cover").attr("abs:src")
        }

        return manga
    }

    // Chapters

    override fun chapterListSelector() = "div.fs-chapter-list div.element"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        chapter.url = element.select("div.title a").attr("href")
        chapter.name = element.select("div.title a").attr("title")
        chapter.date_upload = parseChapterDate(element.select("div.meta_r").text())

        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val lcDate = date.lowercase(Locale.ROOT)
        if (lcDate.startsWith("il y a")) {
            parseRelativeDate(lcDate).let { return it }
        }

        // Handle 'day before yesterday', yesterday' and 'today', using midnight
        var relativeDate: Calendar? = null
        // Result parsed but no year, copy current year over
        when {
            lcDate.startsWith("avant-hier") -> {
                relativeDate = Calendar.getInstance()
                relativeDate.add(Calendar.DAY_OF_MONTH, -2) // day before yesterday
                relativeDate.set(Calendar.HOUR_OF_DAY, 0)
                relativeDate.set(Calendar.MINUTE, 0)
                relativeDate.set(Calendar.SECOND, 0)
                relativeDate.set(Calendar.MILLISECOND, 0)
            }
            lcDate.startsWith("hier") -> {
                relativeDate = Calendar.getInstance()
                relativeDate.add(Calendar.DAY_OF_MONTH, -1) // yesterday
                relativeDate.set(Calendar.HOUR_OF_DAY, 0)
                relativeDate.set(Calendar.MINUTE, 0)
                relativeDate.set(Calendar.SECOND, 0)
                relativeDate.set(Calendar.MILLISECOND, 0)
            }
            lcDate.startsWith("aujourd'hui") -> {
                relativeDate = Calendar.getInstance()
                relativeDate.set(Calendar.HOUR_OF_DAY, 0) // today
                relativeDate.set(Calendar.MINUTE, 0)
                relativeDate.set(Calendar.SECOND, 0)
                relativeDate.set(Calendar.MILLISECOND, 0)
            }
        }

        return relativeDate?.timeInMillis ?: 0L
    }

    private fun parseRelativeDate(date: String): Long {
        val value = date.split(" ")[3].toIntOrNull()

        return if (value != null) {
            when (date.split(" ")[4]) {
                "minute", "minutes" -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "heure", "heures" -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "jour", "jours" -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "semaine", "semaines" -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * 7 * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "mois" -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "an", "ans", "année", "années" -> Calendar.getInstance().apply {
                    add(Calendar.YEAR, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                else -> {
                    return 0L
                }
            }
        } else {
            try {
                SimpleDateFormat("dd MMM yyyy", Locale.FRENCH).parse(date.substringAfter("le "))?.time ?: 0
            } catch (_: Exception) {
                0L
            }
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.fs-read img[id]").forEachIndexed { i, img ->
            pages.add(Page(i, "", img.attr("abs:src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
