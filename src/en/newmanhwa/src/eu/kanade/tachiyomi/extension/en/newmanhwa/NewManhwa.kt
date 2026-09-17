package eu.kanade.tachiyomi.extension.en.newmanhwa

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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class NewManhwa : KeiSource() {
    override val supportsFilterFetching = true

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/popular?page=$page")
        return parseMangaList(response.asJsoup())
    }

    private fun parseMangaList(document: Document): MangasPage {
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
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/latest?page=$page")
        return parseMangaList(response.asJsoup())
    }

    // ========================= Search =========================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchUrl = buildSearchMangaUrl(page, query, filters)
        val response = client.get(searchUrl)
        val document = response.asJsoup()

        if (document.selectFirst("aside.series-left") != null) {
            val manga = parseMangaDetails(document).apply {
                url = response.request.url.encodedPath
            }
            return MangasPage(listOf(manga), false)
        }

        return parseMangaList(document)
    }

    private fun buildSearchMangaUrl(page: Int, query: String, filters: FilterList): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment("series")
        addQueryParameter("q", query)
        filters.forEach { filter ->
            when (filter) {
                // To Do: Completed uses en/completed but ongoing and hiatus isn't currently supported by the source.
                // Completed also doesn't work with Search queries as of now but wroks with genre baseurl/en/completed?q=&genre=comic
                is StatusFilter -> {
                    if (filter.state > 0) {
                        addQueryParameter("status", filter.values[filter.state])
                    }
                }

                is GenreFilter -> {
                    if (filter.state > 0) {
                        addQueryParameter("genre", filter.values[filter.state].value)
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

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && url.host != MIRROR_HOSTS) return null

        val newUrl = baseUrl.toHttpUrl().newBuilder()
            .encodedPath(url.encodedPath)
            .encodedQuery(url.encodedQuery)
            .build()

        val response = client.get(newUrl)

        return parseMangaDetails(response.asJsoup()).apply {
            setUrlWithoutDomain(newUrl.encodedPath)
        }
    }

    // ========================= Details =========================
    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
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

    private fun metaValue(document: Document, label: String): String? = document.select("aside.series-v72-sidebar .series-v72-meta span")
        .find { it.text().equals(label, ignoreCase = true) }
        ?.parent()
        ?.selectFirst("strong")
        ?.text()

    // ========================= Chapters =========================
    private fun parseChapterList(document: Document): List<SChapter> = document.select("div.series-v72-chapter-list a.series-v72-chapter-row").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = element.selectFirst(".series-chapter-number-text")!!.text()
            date_upload = element.selectFirst("time.series-chapter-date")?.attr("datetime")?.let {
                runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrDefault(0L)
            } ?: 0L
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(baseUrl + manga.url)
        val document = response.asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    // ========================= Pages =========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(baseUrl + chapter.url)
        return parsePageList(response.asJsoup())
    }

    private fun parsePageList(document: Document): List<Page> = document.select("div.reader-pages img").mapIndexed { i, element ->
        val url = element.attr("abs:data-src").ifEmpty { element.attr("abs:src") }
        Page(i, "", url)
    }

    // ========================= Filters =========================

    override suspend fun fetchFilterData(): JsonElement {
        val response = client.get("$baseUrl/en/genres")
        val document = response.asJsoup()

        val genres = document.select("a.panel.genre-card").map { el ->
            buildJsonObject {
                put("name", el.selectFirst("strong")!!.text())
                put("slug", el.attr("abs:href").substringAfterLast("/"))
            }
        }

        return buildJsonObject {
            put("genres", JsonArray(genres))
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = getGenreList(data)

        return FilterList(
            buildList {
                add(StatusFilter())
                add(SortFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
            },
        )
    }

    // ========================= Helpers =========================
    private fun String.removeTitleRank(): String = replace(TITLE_RANK_REGEX, "").trim()

    companion object {
        private val MIRROR_HOSTS = "saymanhwa.com"
        private val GENRE_REGEX = "\"genre\":\\s*\\[(.*?)\\]".toRegex()
        private val TITLE_RANK_REGEX = "^#\\d+\\s+".toRegex()
        private val TAGS_MARKER_REGEX = "\\s*\\bTags\\b\\s*".toRegex()
    }
}
