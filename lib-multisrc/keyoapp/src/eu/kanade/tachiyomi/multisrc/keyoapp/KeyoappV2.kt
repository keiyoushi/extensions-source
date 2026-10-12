package eu.kanade.tachiyomi.multisrc.keyoapp

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class KeyoappV2 : KeyoappBase() {

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int) = fetchMangaListPage(page, sort = "popular")

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("_ajax", "recent-chapters")
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select("article.ru-card").map { it.toLatestSManga() }
        val hasNextPage = document.selectFirst("button[aria-label=\"Next recently updated page\"]:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toLatestSManga() = SManga.create().apply {
        val link = selectFirst("h2.ru-title > a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = selectFirst("img.ru-cover-img")?.attrOrNull("data-src")
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = fetchMangaListPage(page, query = query, filters = filters)

    private suspend fun fetchMangaListPage(
        page: Int,
        query: String? = null,
        filters: FilterList = FilterList(),
        sort: String? = null,
    ): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("_ajax", "search")
            query?.takeIf(String::isNotBlank)?.let { addQueryParameter("q", it) }
            sort?.let { addQueryParameter("sort", it) }
            addQueryParameter("offset", ((page - 1) * 20).toString())

            filters.filterIsInstance<SelectFilter>().forEach { it.addToUri(this) }
            filters.filterIsInstance<MultiSelectFilter>().forEach { it.addToUri(this) }
        }.build()

        val data = client.get(url).parseAs<AjaxSearchResponseDto>()
        val mangas = data.html.asJsoup(baseUrl).select("a.group[href]").filterNot { it.isNovel() }.map { it.toSManga() }
        return MangasPage(mangas, data.hasMore)
    }

    private fun Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        title = selectFirst("h2")!!.text()
        thumbnail_url = selectFirst("img")?.attrOrNull("data-src")
    }

    // ========================= Details + Chapters ========================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        url.pathSegments.filter(String::isNotEmpty).singleOrNull() ?: return null

        val document = client.get(url).asJsoup()

        document.selectFirst("[data-series-uid]")?.let {
            val slug = it.attr("data-series-slug")
            return parseMangaDetails(document, slug).apply { initialized = true }
        }

        // Series slug from a chapter page
        val seriesSlug = document.selectFirst("#sidebar-ch-list[data-series-slug]")?.attr("data-series-slug")
            ?: return null
        return parseMangaDetails(client.get(seriesUrl(seriesSlug)).asJsoup(), seriesSlug).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.removePrefix("/")
        val detailsDeferred = if (fetchDetails) {
            async {
                val document = client.get(seriesUrl(slug)).asJsoup()
                if (document.selectFirst("[data-series-uid]") == null) {
                    throw Exception("Series not found")
                }
                parseMangaDetails(document, slug)
            }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async { fetchChapterList(slug) }
        } else {
            null
        }

        SMangaUpdate(
            detailsDeferred?.await() ?: manga,
            chaptersDeferred?.await() ?: chapters,
        )
    }

    private fun seriesUrl(slug: String): HttpUrl = baseUrl.toHttpUrl().newBuilder().addPathSegment(slug).build()

    private fun parseMangaDetails(document: Document, slug: String): SManga = SManga.create().apply {
        url = seriesUrl(slug).encodedPath
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst("#series-desc")?.wholeText()?.trim()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attrOrNull("content")
        genre = document.select("a[href*=\"?browse&tag\"]").eachText().joinToString().ifBlank { null }
        author = metaValue(document, "Author")
        artist = metaValue(document, "Artist")
        status = document.selectFirst("div.text-xs:containsOwn(Status) + div span.text-sm").parseStatus()
    }

    private fun metaValue(document: Document, label: String): String? = document.selectFirst("span.text-xs:containsOwn($label)")
        ?.parent()
        ?.nextElementSibling()
        ?.textOrNull()

    private suspend fun fetchChapterList(slug: String): List<SChapter> {
        val apiUrl = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("_chapters_html", "1")
            .addQueryParameter("series_slug", slug)
            .build()

        val data = client.get(apiUrl).parseAs<ChaptersResponseDto>()
        val rowsDocument = data.rowsHtml.asJsoup(baseUrl)

        return rowsDocument.select("a.chapter-row[href]")
            .filter { showPaidChapters || it.attr("data-ch-locked") != "1" }
            .map { element ->
                val locked = element.attr("data-ch-locked") == "1"
                val label = element.attr("data-ch-label")
                SChapter.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    name = if (locked) "🔒 $label" else label
                    element.selectFirst(".ch-date-row span:last-child")?.text()?.let { date_upload = it.parseDate() }
                }
            }
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("#strip-reader img.reader-page")
        .mapIndexed { i, element -> Page(i, imageUrl = element.absUrl("src")) }

    // ============================== Filters ==============================

    override suspend fun fetchFilterData(): JsonElement {
        val url = baseUrl.toHttpUrl().newBuilder().addQueryParameter("browse", "1").build()
        val document = client.get(url).asJsoup()
        val tags = document.selectFirst("#search-genres-list[data-genre-tags]")?.attr("data-genre-tags")
            ?: throw Exception("Genre tags not found")
        return tags.parseAs<List<String>>().toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<String>>().orEmpty()

        return FilterList(
            buildList {
                add(SelectFilter("Type", "type", TYPES))
                add(SelectFilter("Status", "status", STATUSES))
                add(SelectFilter("Sort by", "sort", SORTS))
                if (genres.isNotEmpty()) add(TagFilter(genres.associateBy { it.replaceFirstChar(Char::uppercase) }))
            },
        )
    }

    companion object {
        private val TYPES = listOf(
            "All" to "",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Mangatoon" to "mangatoon",
            "Manga" to "manga",
        )

        private val STATUSES = listOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
        )

        private val SORTS = listOf(
            "Latest" to "latest",
            "Trending" to "trending",
            "Popular" to "popular",
            "Most Viewed" to "views",
            "Top Rated" to "rating",
            "A-Z" to "az",
            "Z-A" to "za",
        )
    }
}
