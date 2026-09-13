package eu.kanade.tachiyomi.extension.en.mangafox

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaFox : KeiSource() {

    private val mobileUrl get() = baseUrl.replace("://", "://m.")

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 1.seconds)
        .addCookie({ mobileUrl.toHttpUrl().host }, "readway" to "2") // Get all page URLs at once
        .addCookie("isAdult" to "1") // NOTE: subdomain must be ordered before the main domain

    private val dateFormat = DateTimeFormatter.ofPattern("MMM d,yyyy", Locale.ENGLISH)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val pageStr = if (page != 1) "$page.html" else ""
        val document = client.get("$baseUrl/directory/$pageStr").asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaSelector() = "ul.manga-list-1-list li"

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("a")!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            title = it.attr("title")
            thumbnail_url = it.selectFirst("img")?.attr("abs:src")
        }
    }

    private fun popularMangaNextPageSelector() = ".pager-list-left a.active + a + a"

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val pageStr = if (page != 1) "$page.html" else ""
        val document = client.get("$baseUrl/directory/$pageStr?latest").asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = mutableListOf<Int>()
        val genresEx = mutableListOf<Int>()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("title", query)
            filters.forEach { filter ->
                when (filter) {
                    is UriPartFilter -> addQueryParameter(filter.query, filter.toUriPart())

                    is GenreFilter -> filter.state.forEach {
                        when (it.state) {
                            Filter.TriState.STATE_INCLUDE -> genres.add(it.id)
                            Filter.TriState.STATE_EXCLUDE -> genresEx.add(it.id)
                            else -> {}
                        }
                    }

                    is FilterWithMethodAndText -> {
                        val method = filter.state[0] as UriPartFilter
                        val text = filter.state[1] as TextSearchFilter
                        addQueryParameter(method.query, method.toUriPart())
                        addQueryParameter(text.query, text.state)
                    }

                    is RatingFilter -> filter.state.forEach {
                        addQueryParameter(it.query, it.toUriPart())
                    }

                    is TextSearchFilter -> addQueryParameter(filter.query, filter.state)

                    else -> {}
                }
            }
            addQueryParameter("genres", genres.joinToString(","))
            addQueryParameter("nogenres", genresEx.joinToString(","))
            addQueryParameter("sort", "")
            addQueryParameter("stype", "1")
        }.build()
        val document = client.get(url).asJsoup()
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaSelector() = "ul.manga-list-4-list li"

    private fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsFromDocument(document), chapterListFromDocument(document))
    }

    private fun mangaDetailsFromDocument(document: Document): SManga = SManga.create().apply {
        document.selectFirst(".detail-info-right")!!.let { it ->
            author = it.select(".detail-info-right-say a").joinToString { it.text() }
            genre = it.select(".detail-info-right-tag-list a").joinToString { it.text() }
            description = it.selectFirst("p.fullcontent")?.text()
            status = it.selectFirst(".detail-info-right-title-tip")?.text().let { parseStatus(it) }
            thumbnail_url = document.selectFirst(".detail-info-cover-img")?.attr("abs:src")
        }
    }

    private fun chapterListFromDocument(document: Document): List<SChapter> = document.select(chapterListSelector()).map { chapterFromElement(it) }

    private fun chapterListSelector() = "ul.detail-main-list li a"

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.selectFirst(".detail-main-list-main p")!!.text()
        date_upload = element.select(".detail-main-list-main p").last()?.text()?.let { parseChapterDate(it) } ?: 0
    }

    private fun parseChapterDate(date: String): Long = if ("Today" in date || " ago" in date) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } else if ("Yesterday" in date) {
        Calendar.getInstance().apply {
            add(Calendar.DATE, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } else {
        dateFormat.tryParseDate(date)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mobilePath = chapter.url.replace("/manga/", "/roll_manga/")

        val headers = headersBuilder().set("Referer", "$mobileUrl/").build()

        val document = client.get("$mobileUrl$mobilePath", headers).asJsoup()
        return document.select("#viewer img").mapIndexed { idx, it ->
            Page(idx, imageUrl = it.attr("abs:data-original"))
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        NameFilter(),
        EntryTypeFilter(),
        CompletedFilter(),
        AuthorFilter(),
        ArtistFilter(),
        RatingFilter(),
        YearFilter(),
        GenreFilter(getGenreList()),
    )
}
