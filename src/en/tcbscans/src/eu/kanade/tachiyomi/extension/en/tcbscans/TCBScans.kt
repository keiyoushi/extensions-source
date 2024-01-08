package eu.kanade.tachiyomi.extension.en.tcbscans

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TCBScans : ParsedHttpSource() {

    override val name = "TCB Scans"
    override val baseUrl = "https://onepiecechapters.com"
    override val lang = "en"
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient
    companion object {
        private const val MIGRATE_MESSAGE = "Migrate from TCB Scans to TCB Scans"
        private val TITLE_REGEX = "[0-9]+$".toRegex()
    }

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/projects")
    }

    override fun popularMangaSelector() = ".bg-card.border.border-border.rounded.p-3.mb-3"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select(".w-24.h-24.object-cover.rounded-lg").attr("src")
        manga.setUrlWithoutDomain(element.select("a.mb-3.text-white.text-lg.font-bold").attr("href"))
        manga.title = element.select("a.mb-3.text-white.text-lg.font-bold").text()
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservable()
            .doOnNext { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw Exception(if (response.code == 404) MIGRATE_MESSAGE else "HTTP error ${response.code}")
                }
            }
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    // latest
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        var mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val query = response.request.headers["query"]
        mangas = if (query != null) {
            mangas.filter { it.title.contains(query, true) }
        } else {
            emptyList()
        }

        return MangasPage(mangas, false)
    }
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String = throw Exception("Not used")

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val headers = headersBuilder()
            .add("query", query)
            .build()
        return GET("$baseUrl/projects", headers)
    }

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val descElement = document.select(".order-1.bg-card.border.border-border.rounded.py-3")

        thumbnail_url = descElement.select(".flex.items-center.justify-center img").attr("src")
        title = descElement.select(".my-3.font-bold.text-3xl").text()
        description = descElement.select(".leading-6.my-3").text()
    }

    // chapters
    override fun chapterListSelector() =
        ".block.border.border-border.bg-card.mb-3.p-3.rounded"

    private fun chapterWithDate(element: Element, slug: String): SChapter {
        val seriesPrefs = Injekt.get<Application>().getSharedPreferences("source_${id}_updateTime:$slug", 0)
        val seriesPrefsEditor = seriesPrefs.edit()

        val chapter = chapterFromElement(element)

        val currentTimeMillis = System.currentTimeMillis()
        if (!seriesPrefs.contains(chapter.name)) {
            seriesPrefsEditor.putLong(chapter.name, currentTimeMillis)
        }

        chapter.date_upload = seriesPrefs.getLong(chapter.name, currentTimeMillis)

        seriesPrefsEditor.apply()
        return chapter
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        chapter.setUrlWithoutDomain(element.attr("href"))

        // Chapters retro compatibility
        var name = element.select(".text-lg.font-bold:not(.flex)").text()
        val description = element.select(".text-gray-500").text()
        val matchResult = TITLE_REGEX.find(name)
        if (matchResult != null) {
            name = "Chapter ${matchResult.value}"
        }
        chapter.name = "$name: $description"

        return chapter
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val slug = response.request.url.pathSegments[2]

        return document.select(chapterListSelector()).map { chapterWithDate(it, slug) }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservable()
            .doOnNext { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw Exception(if (response.code == 404) MIGRATE_MESSAGE else "HTTP error ${response.code}")
                }
            }
            .map { response ->
                pageListParse(response)
            }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".flex.flex-col.items-center.justify-center picture img")
            .mapIndexed { i, el -> Page(i, "", el.attr("src")) }
    }

    override fun imageUrlParse(document: Document) = ""
}
