package eu.kanade.tachiyomi.extension.en.newmanhwa

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class NewManhwa : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/popular?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = popularMangaParse(response.asJsoup())

    private fun popularMangaParse(document: Document): MangasPage {
        val mangas = document.select("article.series-card").map { element ->
            SManga.create().apply {
                val coverLink = element.selectFirst("a.series-card-cover")!!
                setUrlWithoutDomain(coverLink.attr("abs:href"))
                title = element.selectFirst("div.series-card-body h2 a")!!.text().removeTitleRank()
                thumbnail_url = coverLink.selectFirst("img")?.let {
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
            addPathSegment("series")
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
        description = document.selectFirst("div.series-v72-description")
            ?.text()
            ?.let { TAGS_MARKER_REGEX.split(it, limit = 2).first() }
            ?.trim()
        author = metaValue(document, "Author")
        artist = metaValue(document, "Artist")
        status = when (metaValue(document, "Status")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("aside.series-left .cover-card img")?.attr("abs:src")

        val sidebarGenres = document.select("div.series-v72-genres a").eachText()
        if (sidebarGenres.isNotEmpty()) {
            genre = sidebarGenres.joinToString()
        } else {
            // Fallback for pages where the sidebar genre list is missing/empty.
            val jsonLd = document.select("script[type=application/ld+json]")
                .find { it.data().contains("\"@type\":\"ComicSeries\"") }
                ?.data()

            jsonLd?.let {
                GENRE_REGEX.find(it)?.groupValues?.get(1)?.let { genresString ->
                    genre = genresString.replace("\"", "").split(",").map { g -> g.trim() }.joinToString()
                }
            }
        }
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    private fun metaValue(document: Document, label: String): String? = document.select("aside.series-v72-sidebar .series-v72-meta span")
        .find { it.text().equals(label, ignoreCase = true) }
        ?.parent()
        ?.selectFirst("strong")
        ?.text()

    // ========================= Chapters =========================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.series-v72-chapter-list a.series-v72-chapter-row").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                name = element.selectFirst(".series-chapter-number-text")!!.text()
                date_upload = element.selectFirst("time.series-chapter-date")?.attr("datetime")?.let {
                    runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrDefault(0L)
                } ?: 0L
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    // ========================= Pages =========================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.reader-pages img").mapIndexed { i, element ->
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

    // ========================= Helpers =========================
    private fun String.removeTitleRank(): String = replace(TITLE_RANK_REGEX, "").trim()

    companion object {
        private val MIRROR_HOSTS = listOf("saymanhwa.com")
        private val GENRE_REGEX = "\"genre\":\\s*\\[(.*?)\\]".toRegex()
        private val TITLE_RANK_REGEX = "^#\\d+\\s+".toRegex()
        private val TAGS_MARKER_REGEX = "\\s*\\bTags\\b\\s*".toRegex()
    }
}
