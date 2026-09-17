package eu.kanade.tachiyomi.multisrc.madara

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.get
import keiyoushi.network.head
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

private const val PAGE_SIZE = 25

abstract class MadaraNoAjax : MadaraBase() {
    override suspend fun getPopularManga(page: Int) = archivePage(page, "views")
    override suspend fun getLatestUpdates(page: Int) = archivePage(page, "latest")

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data.genreRoutes()
        return FilterList(
            buildList {
                add(SortFilter(intl["order_by_filter_title"], orderByFilterOptions))
                if (genres.isNotEmpty()) add(SingleGenreFilter(intl["genre_filter_title"], intl["adult_content_filter_all"], genres))
            },
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<SingleGenreFilter>()?.route()
        val sort = filters.firstInstanceOrNull<SortFilter>()?.key().orEmpty()
        if (genre != null) return archivePage(page, sort, genre.path, query)
        return if (query.isBlank()) archivePage(page, sort) else htmlSearch(page, query)
    }

    protected suspend fun archivePage(page: Int, order: String, path: String = "/$mangaSubString/", query: String = ""): MangasPage {
        val url = baseUrl.toHttpUrl().resolve(path)!!.newBuilder().apply {
            if (page > 1) addPathSegments("page/$page/")
            if (order.isNotBlank()) addQueryParameter("m_orderby", order)
            if (query.isNotBlank()) addQueryParameter("s", query)
        }.build()
        val document = client.get(url).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst("div.nav-previous, a.nextpostslink") != null)
    }

    private suspend fun htmlSearch(page: Int, query: String): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("s", query)
            addQueryParameter("post_type", "wp-manga")
        }.build()
        val document = client.get(url).asJsoup()
        val archiveMangas = parseArchive(document)
        if (archiveMangas.isNotEmpty()) return MangasPage(archiveMangas, false)

        val cards = parseSearchCards(document)
        val ids = rssIds(query, cards.map(SearchCard::path))
        if (!ids.complete) {
            return MangasPage(
                cards.mapNotNull { card ->
                    resolvePostId(baseUrl.toHttpUrl().resolve(card.path)!!)?.let { id ->
                        SManga.create().apply {
                            this.url = id
                            title = card.title
                            thumbnail_url = card.thumbnail
                            memo = mangaMemo(card.path, emptyList())
                        }
                    }
                },
                false,
            )
        }
        return MangasPage(
            cards.mapNotNull { card ->
                ids.values[card.path]?.let { id ->
                    SManga.create().apply {
                        this.url = id
                        title = card.title
                        thumbnail_url = card.thumbnail
                        memo = mangaMemo(card.path, emptyList())
                    }
                }
            },
            false,
        )
    }

    private suspend fun rssIds(query: String, needed: List<String>): RssIds {
        val values = mutableMapOf<String, String>()
        for (page in 1..20) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("feed")
                .addQueryParameter("post_type", "wp-manga")
                .addQueryParameter("s", query)
                .addQueryParameter("paged", page.toString())
                .build()
            val feed = runCatching { parseRss(client.get(url)) }.getOrElse { break }
            feed.forEach { (id, card) -> values[card.path] = id }
            if (needed.all(values::containsKey) || feed.isEmpty()) break
        }
        return RssIds(values, needed.all(values::containsKey))
    }

    private fun parseRss(response: Response): Map<String, SearchCard> {
        val document = Jsoup.parse(response.body.string(), baseUrl, Parser.xmlParser())
        return document.select("item").mapNotNull { item ->
            val link = item.selectFirst("link")?.text()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val id = item.selectFirst("guid")?.text()?.toHttpUrlOrNull()?.queryParameter("p") ?: return@mapNotNull null
            id to SearchCard(item.selectFirst("title")!!.text(), link.toHttpUrl().encodedPath, item.selectFirst("media|content")?.attr("url"))
        }.toMap()
    }

    private suspend fun resolvePostId(url: HttpUrl): String? {
        client.head(url, ensureSuccess = false).use { response ->
            response.headers.values("Link").firstNotNullOfOrNull { it.shortlinkId() }?.let { return it }
        }
        return client.get(url).asJsoup().mangaId()
    }

    override suspend fun fetchRelatedMangaList(id: String, genres: List<GenreRoute>): List<SManga> = coroutineScope {
        val lists = genres.map { genre -> async { archivePage(1, "", genre.path).mangas } }.awaitAll()
        lists.flatten().filterNot { it.url == id }.withIndex().groupBy { it.value.url }.values.sortedWith(compareByDescending<List<IndexedValue<SManga>>> { it.size }.thenBy { it.minOf(IndexedValue<SManga>::index) }).map { it.first().value }.take(PAGE_SIZE)
    }

    private class RssIds(val values: Map<String, String>, val complete: Boolean)

    private fun String.shortlinkId(): String? {
        if (!contains("rel=shortlink", ignoreCase = true) && !contains("rel=\"shortlink\"", ignoreCase = true)) return null
        return substringAfter('<', "").substringBefore('>', "").toHttpUrlOrNull()?.queryParameter("p")
    }
}
