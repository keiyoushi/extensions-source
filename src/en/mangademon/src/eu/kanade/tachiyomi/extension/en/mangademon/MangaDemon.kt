package eu.kanade.tachiyomi.extension.en.mangademon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaDemon : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(6) { it.toString().contains("images/thumbnails") }
        rateLimit(2)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = mangaList("$baseUrl/advanced.php?list=$page&status=all&orderby=VIEWS%20DESC".toHttpUrl())

    private suspend fun mangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select("div#advanced-content > div.advanced-element").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.encodedAttr("href"))
                title = element.selectFirst("h1")!!.ownText()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("div.pagination > ul > a > li:contains(Next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/lastupdates.php?list=$page").asJsoup()
        val mangas = document.select("div#updates-container > div.updates-element:not(:has(.toffee-badge))").map { element ->
            SManga.create().apply {
                with(element.selectFirst("div.updates-element-info")!!) {
                    setUrlWithoutDomain(selectFirst("a")!!.encodedAttr("href"))
                    title = selectFirst("a")!!.ownText()
                }
                thumbnail_url = element.selectFirst("div.thumb img")!!.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("div.pagination > ul > a > li:contains(Next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isEmpty()) {
            return mangaList(filterSearchUrl(page, filters))
        }

        val url = "$baseUrl/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("manga", query)
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select("body > a[href]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.encodedAttr("href"))
                title = element.selectFirst("div.seach-right > div")!!.ownText()
                thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    private fun filterSearchUrl(page: Int, filters: FilterList): HttpUrl = "$baseUrl/advanced.php".toHttpUrl().newBuilder().apply {
        addQueryParameter("list", page.toString())
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    filter.checked.forEach { genre ->
                        addQueryParameter("genre[]", genre)
                    }
                }

                is StatusFilter -> {
                    addQueryParameter("status", filter.selected)
                }

                is SortFilter -> {
                    addQueryParameter("orderby", filter.selected)
                }

                else -> {}
            }
        }
    }.build()

    override fun getFilterList(data: JsonElement?) = getFilters()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        manga.apply {
            with(document.selectFirst("div#manga-info-container")!!) {
                title = selectFirst("h1.big-fat-titles")!!.ownText()
                thumbnail_url = selectFirst("div#manga-page img")!!.attr("abs:src")
                genre = select("div.genres-list > li").joinToString { it.text() }
                description = selectFirst("div#manga-info-rightColumn > div > div.white-font")!!.text()
                author = parseAuthor(select("div#manga-info-stats > div:has(> li:eq(0):contains(Author)) > li:eq(1)").text())
                status = parseStatus(select("div#manga-info-stats > div:has(> li:eq(0):contains(Status)) > li:eq(1)").text())
            }
        }

        val chapterList = document.select("div#chapters-list a.chplinks").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.encodedAttr("href"))
                name = element.ownText()
                date_upload = DATE_FORMATTER.tryParseDate(element.selectFirst("span")?.ownText())
            }
        }

        return SMangaUpdate(manga, chapterList)
    }

    private fun parseAuthor(author: String?) = when {
        author == null || author.contains("Updating", ignoreCase = true) -> null
        else -> author
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        status.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div > img.imgholder").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    private fun Element.encodedAttr(attribute: String) = URLEncoder.encode(attr(attribute), "UTF-8")

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
    }
}
