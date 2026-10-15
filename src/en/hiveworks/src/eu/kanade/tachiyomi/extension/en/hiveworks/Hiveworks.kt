package eu.kanade.tachiyomi.extension.en.hiveworks

import android.net.Uri
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

/**
 * Code that used to handle Saturday Morning Breakfast Comics has been split to its
 * own separate extension at eu.kanade.tachiyomi.extension.en.saturdaymorningbreakfastcomics
 */
@Source
abstract class Hiveworks : KeiSource() {

    // Client

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = connectTimeout(1.minutes)
        .readTimeout(1.minutes)
        .retryOnConnectionFailure(true)
        .followRedirects(true)

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage = comicBlocksParse(client.get(baseUrl))

    private fun comicBlocksParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(POPULAR_MANGA_SELECTOR).filterNot {
            val url = it.select("a.comiclink").first()!!.attr("abs:href")
            url.contains("sparklermonthly.com") || url.contains("explosm.net") // Filter Unsupported Comics
        }.map { element ->
            mangaFromElement(element)
        }

        return MangasPage(mangas, false)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val day = LocalDate.now().dayOfWeek.name.lowercase(Locale.US)
        return comicBlocksParse(client.get("$baseUrl/home/update-day/$day"))
    }

    // Search
    // Source's website doesn't appear to have a search function; so searching locally

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val uri = Uri.parse(baseUrl).buildUpon()
        if (filters.isNotEmpty()) uri.appendPath("home")
        // Append uri filters
        filters.forEach { filter ->
            when (filter) {
                is UriFilter -> filter.addToUri(uri)
                is OriginalsFilter -> if (filter.state) return searchList("$baseUrl/originals", transform = ::searchOriginalMangaFromElement)
                is KidsFilter -> if (filter.state) return searchList("$baseUrl/kids")
                is CompletedFilter -> if (filter.state) return searchList("$baseUrl/completed")
                is HiatusFilter -> if (filter.state) return searchList("$baseUrl/hiatus")
                else -> { /*Do nothing*/ }
            }
        }
        return searchList(uri.toString(), query)
    }

    private suspend fun searchList(
        url: String,
        query: String = "",
        transform: (Element) -> SManga = ::mangaFromElement,
    ): MangasPage {
        val document = client.get(url).asJsoup()

        val mangas = document.select(SEARCH_MANGA_SELECTOR)
            .filter { query.isEmpty() || it.text().contains(query, true) }
            .map(transform)

        return MangasPage(mangas, false)
    }

    private fun searchOriginalMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img")[1].attr("abs:src")
        title = element.select("div.header").text().substringBefore("by").trim()
        author = element.select("div.header").text().substringAfter("by").trim()
        artist = author
        description = element.select("div.description").text()
        url = element.select("a").first()!!.attr("href")
    }

    // Common

    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.select("a.comiclink").first()!!.attr("abs:href")
        manga.title = element.select("h1").text()
        manga.thumbnail_url = element.select("img").attr("abs:src")
        manga.artist = element.select("h2").text().removePrefix("by").trim()
        manga.author = manga.artist
        manga.description = element.select("div.description").text()
        manga.genre = element.select("div.comicrating").text()
        return manga
    }

    override fun getMangaUrl(manga: SManga): String = manga.url

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    // Details
    // Fetches details by calling home page again and using the existing url to find the correct comic

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val document = getWithErrors(baseUrl).asJsoup()
        return document.select(POPULAR_MANGA_SELECTOR)
            .firstOrNull { manga.url == it.select("a.comiclink").first()!!.attr("abs:href") }
            ?.let { mangaFromElement(it) } ?: manga
    }

    // Chapters

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        if (manga.status == SManga.LICENSED) throw Exception("Licensed - No chapters to show")

        val uri = Uri.parse(manga.url).buildUpon()
        when {
            "sssscomic" in uri.toString() -> uri.appendQueryParameter("id", "archive")

            // sssscomic uses query string in url
            "awkwardzombie" in uri.toString() -> uri.appendPath("awkward-zombie").appendPath("archive")

            "smbc-comics" in uri.toString() -> throw Exception("Migrate to the Saturday Morning Breakfast Comics extension to read this comic")

            else -> {
                uri.appendPath("comic")
                uri.appendPath("archive")
            }
        }
        return chapterListParse(getWithErrors(uri.toString()))
    }

    private fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url.toString()
        when {
            "witchycomic" in url -> return witchyChapterListParse(response)
            "sssscomic" in url -> return ssssChapterListParse(response)
            "awkwardzombie" in url -> return awkwardzombieChapterListParse(response)
        }
        val document = response.asJsoup()
        val baseUrl = document.select("div script").html().substringAfter("href='").substringBefore("'")
        val elements = document.select(CHAPTER_LIST_SELECTOR)
        if (elements.isNullOrEmpty()) throw Exception("This comic has a unsupported chapter list")
        val chapters = mutableListOf<SChapter>()
        for (i in 1 until elements.size) {
            chapters.add(createChapter(elements[i], baseUrl))
        }
        when {
            "checkpleasecomic" in url -> chapters.retainAll { it.name.endsWith("01") || it.name.endsWith(" 1") }
        }
        chapters.reverse()
        return chapters
    }

    private fun createChapter(element: Element, baseUrl: String?) = SChapter.create().apply {
        name = element.text().substringAfter("-").trim()
        url = baseUrl + element.attr("value")
        date_upload = DATE_FORMATTER.tryParseDate(element.text().substringBefore("-").trim())
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(chapter.url)
        val url = response.request.url
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()

        document.select("div#cc-comicbody img").forEach {
            pages.add(Page(pages.size, imageUrl = it.attr("src")))
        }

        // Site specific pages can be added here
        when {
            "sssscomic" in url.toString() -> {
                val urlPath = document.select("img.comicnormal").attr("src")
                val urlimg = url.resolve("../../$urlPath").toString()
                pages.add(Page(pages.size, imageUrl = urlimg))
            }
            else -> { /*Do Nothing*/ }
        }

        return pages
    }

    // Filters

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Only one filter can be used at a time"),
        Filter.Separator(),
        UpdateDay(),
        RatingFilter(),
        GenreFilter(),
        TitleFilter(),
        SortFilter(),
        Filter.Separator(),
        Filter.Header("Extra Lists"),
        OriginalsFilter(),
        KidsFilter(),
        CompletedFilter(),
        HiatusFilter(),
    )

    // Other Code

    private fun awkwardzombieChapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        response.asJsoup().select("div.archive-line").forEach {
            chapters.add(
                SChapter.create().apply {
                    val chapterNumber = it.select(".archive-date").text().substringAfter("#").substringBefore(",")
                    chapter_number = chapterNumber.toFloat()
                    name = "#$chapterNumber ${it.select("div.archive-title").text()} (${it.select(".archive-game").text()})"
                    url = it.select("a").attr("abs:href")
                    date_upload = AWKWARDZOMBIE_DATE_FORMAT.tryParseDate(it.select(".archive-date").text().substringAfter(", "))
                },
            )
        }
        return chapters
    }

    // Gets the chapter list for witchycomic
    private fun witchyChapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val elements = document.select(".cc-storyline-pagethumb a")
        if (elements.isNullOrEmpty()) throw Exception("This comic has a unsupported chapter list")
        val chapters = mutableListOf<SChapter>()
        for (i in 1 until elements.size) {
            val chapter = SChapter.create()
            chapter.name = "Page " + i
            chapter.url = elements[i].attr("href")
            // Date upload isn't available for witchy, unfortunately. As a
            // workaround to ensure notifications work, use system time.
            chapter.date_upload = System.currentTimeMillis()
            chapters.add(chapter)
        }
        chapters.retainAll { it.url.contains("page-") }
        chapters.reverse()
        return chapters
    }

    /**
     * Gets the chapter list for sssscomic - based on work by roblabla for witchycomic
     *
     */
    private fun ssssChapterListParse(response: Response): List<SChapter> {
        val requestUrl = response.request.url
        val document = response.asJsoup()
        // Gets the adventure div's
        val advDiv = document.select("div[id^=adv]")
        val chapters = mutableListOf<SChapter>()
        // Iterate through the Div's
        for (i in 1 until advDiv.size + 1) {
            val elements = document.select("#adv${i}Div a")
            if (elements.isNullOrEmpty()) throw Exception("This comic has a unsupported chapter list")
            for (c in 0 until elements.size) {
                val chapter = SChapter.create()
                // Adventure No. and Page No. for chapter name
                chapter.name = "Adventure $i - Page ${elements[c].text()}"
                // Uses relative paths so need to combine the initial host with the path
                val urlPath = elements[c].attr("href")
                chapter.url = requestUrl.resolve("../../$urlPath").toString()
                // use system time as the date of the chapters are per page and takes to long to pull each one.
                chapter.date_upload = System.currentTimeMillis()
                chapters.add(chapter)
            }
        }
        chapters.retainAll { it.url.contains("page") }
        chapters.reverse()
        return chapters
    }

    // Used to throw custom error codes for http codes
    private suspend fun getWithErrors(url: String): Response {
        val response = client.get(url, ensureSuccess = false)
        if (!response.isSuccessful) {
            response.close()
            when (response.code) {
                404 -> throw Exception("This comic has a unsupported chapter list")
                else -> throw Exception("HiveWorks Comics HTTP Error ${response.code}")
            }
        }
        return response
    }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("[MMMM][MMM] d, yyyy", Locale.US)
        private val AWKWARDZOMBIE_DATE_FORMAT = DateTimeFormatter.ofPattern("M-d-yy", Locale.US)

        private const val POPULAR_MANGA_SELECTOR = "div.comicblock"
        private const val SEARCH_MANGA_SELECTOR = "div.comicblock, div.originalsblock"
        private const val CHAPTER_LIST_SELECTOR = "select[name=comic] option"
    }
}
