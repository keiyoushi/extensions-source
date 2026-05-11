package eu.kanade.tachiyomi.extension.en.newmanhwa

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class NewManhwa :
    HttpSource(),
    ConfigurableSource {

    override val name = "New Manhwa"

    override val baseUrl by lazy {
        preferences.getString(PREF_BASE_URL, DEFAULT_BASE_URL)!!
    }

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/popular?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = popularMangaParse(response.asJsoup())

    private fun popularMangaParse(document: Document): MangasPage {
        val mangas = document.select("a.series-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.selectFirst("strong")!!.text().removeTitleRank()
                thumbnail_url = element.selectFirst("img")?.let {
                    it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
                }
            }
        }
        val hasNextPage = document.selectFirst("a:contains(Next):not(.disabled)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Latest =========================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ========================= Search =========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val baseHttpUrl = baseUrl.toHttpUrl()
        val queryUrl = query.toHttpUrlOrNull()
        if (queryUrl != null && MIRROR_HOSTS.contains(queryUrl.host)) {
            val newUrl = baseHttpUrl.newBuilder()
                .encodedPath(queryUrl.encodedPath)
                .encodedQuery(queryUrl.encodedQuery)
                .build()
            return GET(newUrl, headers)
        }

        val url = baseHttpUrl.newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("q", query)
            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter("status", filter.values[filter.state])
                        }
                    }

                    is GenreFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter("genre", filter.values[filter.state])
                        }
                    }

                    is SortFilter -> {
                        val sortValue = when (filter.state) {
                            0 -> "updated"
                            1 -> "popular"
                            2 -> "chapters"
                            3 -> "newest"
                            4 -> "az"
                            5 -> "za"
                            else -> "updated"
                        }
                        addQueryParameter("sort", sortValue)
                    }

                    else -> {}
                }
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (document.selectFirst("aside.series-left") != null) {
            val manga = mangaDetailsParse(document).apply {
                url = response.request.url.encodedPath
            }
            return MangasPage(listOf(manga), false)
        }
        return popularMangaParse(document)
    }

    // ========================= Details =========================
    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst("section.summary-inline p")?.text()
        author = document.selectFirst("dt:contains(Author) + dd a span")?.text()
        artist = document.selectFirst("dt:contains(Artist) + dd a span")?.text()
        status = when (document.selectFirst("dt:contains(Status) + dd span")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("aside.series-left .cover-card img")?.attr("abs:src")

        val jsonLd = document.select("script[type=application/ld+json]")
            .find { it.data().contains("\"@type\":\"ComicSeries\"") }
            ?.data()

        if (jsonLd != null) {
            GENRE_REGEX.find(jsonLd)?.groupValues?.get(1)?.let { genresString ->
                genre = genresString.replace("\"", "").split(",").map { it.trim() }.joinToString()
            }
        }
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    // ========================= Chapters =========================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chapter-list a.chapter-row").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.selectFirst(".chapter-name strong")!!.text()
                date_upload = element.selectFirst(".chapter-age")?.text()?.let {
                    DATE_FORMAT.tryParse(it)
                } ?: 0L
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    // ========================= Pages =========================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("main#reader img.chapter-page").mapIndexed { i, element ->
            val url = element.attr("abs:data-src").ifEmpty { element.attr("abs:src") }
            Page(i, "", url)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ========================= Filters =========================
    override fun getFilterList() = FilterList(
        StatusFilter(),
        GenreFilter(),
        SortFilter(),
    )

    // ========================= Preferences =========================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_BASE_URL
            title = "Mirror"
            entries = MIRRORS
            entryValues = MIRRORS
            summary = "%s"
            setDefaultValue(DEFAULT_BASE_URL)
        }.also(screen::addPreference)
    }

    // ========================= Helpers =========================
    private fun String.removeTitleRank(): String = replace(TITLE_RANK_REGEX, "").trim()

    companion object {
        private const val PREF_BASE_URL = "pref_base_url"
        private const val DEFAULT_BASE_URL = "https://newmanhwa.com"
        private val MIRRORS = arrayOf(
            "https://newmanhwa.com",
            "https://fullmanhwa.com",
        )
        private val MIRROR_HOSTS = MIRRORS.map { it.toHttpUrl().host }

        private val DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        private val GENRE_REGEX = "\"genre\":\\s*\\[(.*?)\\]".toRegex()
        private val TITLE_RANK_REGEX = "^#\\d+\\s+".toRegex()
    }
}
