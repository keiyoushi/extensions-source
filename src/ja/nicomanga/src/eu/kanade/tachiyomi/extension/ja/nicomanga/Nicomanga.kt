package eu.kanade.tachiyomi.extension.ja.nicomanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.Calendar

class Nicomanga : HttpSource() {

    override val name = "Nicomanga"

    override val baseUrl = "https://nicomanga.com"

    override val lang = "ja"

    override val supportsLatest = true

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-list.html?p=$page&pr=popular", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".manga-grid .manga-card").map { element ->
            SManga.create().apply {
                title = element.select(".manga-title").text()
                // The .manga-title contains the manga detail link, while .manga-cover links to the latest chapter
                setUrlWithoutDomain(element.select(".manga-title").attr("abs:href"))
                thumbnail_url = element.select("img.manga-img").let { img ->
                    img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
                }
            }
        }

        val hasNextPage = document.select(".custom-pagination .page-link.next:not(.disabled)").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga-list.html?p=$page&pr=new", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("p", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("n", query.trim())
        }

        filters.firstInstanceOrNull<SortFilter>()?.state?.let { state ->
            val sortables = arrayOf("last_update", "views", "post", "name")
            val prs = arrayOf("all", "popular", "new", "az")

            url.addQueryParameter("s", sortables[state.index])
            url.addQueryParameter("pr", prs[state.index])
            url.addQueryParameter("st", if (state.ascending) "ASC" else "DESC")
        }

        val genres = filters.firstInstanceOrNull<GenreList>()?.state
            ?.filter { it.state }
            ?.map { it.id }
            ?: emptyList()

        if (genres.isNotEmpty()) {
            url.addQueryParameter("g", genres.joinToString(","))
            val logicFilter = filters.firstInstanceOrNull<MatchingLogic>()
            url.addQueryParameter("gm", (logicFilter?.state ?: 1).toString())
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.select(".manga-main-title").text()
            thumbnail_url = document.select(".manga-cover-image").attr("abs:src")
            author = document.select(".info-field-label:contains(Author) + .info-field-value a").text()
            genre = document.select(".info-field-label:contains(Genre) + .info-field-value a").joinToString { it.text() }
            status = when (document.select(".info-field-label:contains(Status) + .info-field-value").text().lowercase()) {
                "on going", "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = document.select(".description-text-content").text()
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("#chapter-grid .chapter-grid-item").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                name = element.select(".chapter-name-grid").text()
                date_upload = parseRelativeDate(element.select(".chapter-time-grid").text())
            }
        }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val jsonString = body.substringAfter("window.chapterImages = ", "").substringBefore(";").trim()

        // Site embeds images directly in an array within a JS script block.
        // Doing regex extraction avoids needing to rely on potentially incomplete or lazy loaded images.
        if (jsonString.startsWith("[")) {
            val images = jsonString.parseAs<List<String>>()
            return images.mapIndexed { i, url ->
                Page(i, imageUrl = url.trim())
            }
        }

        // Fallback to DOM parsing just in case structure changes
        val document = Jsoup.parseBodyFragment(body, baseUrl)
        return document.select("#chapter_images_container .chapter-image-wrapper img").mapIndexed { i, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        MatchingLogic(),
        Filter.Separator(),
        GenreList(getGenreList()),
    )

    // ============================= Utilities =============================

    private fun parseRelativeDate(dateString: String): Long {
        val trimmed = dateString.trim().lowercase()
        val number = trimmed.substringBefore(" ").toIntOrNull() ?: return 0L
        val cal = Calendar.getInstance()

        when {
            trimmed.contains("year") -> cal.add(Calendar.YEAR, -number)
            trimmed.contains("month") -> cal.add(Calendar.MONTH, -number)
            trimmed.contains("week") -> cal.add(Calendar.WEEK_OF_YEAR, -number)
            trimmed.contains("day") -> cal.add(Calendar.DAY_OF_YEAR, -number)
            trimmed.contains("hour") -> cal.add(Calendar.HOUR, -number)
            trimmed.contains("minute") || trimmed.contains("min") -> cal.add(Calendar.MINUTE, -number)
            trimmed.contains("second") || trimmed.contains("sec") -> cal.add(Calendar.SECOND, -number)
            else -> return 0L
        }

        return cal.timeInMillis
    }
}
