package eu.kanade.tachiyomi.extension.all.xasiatalbums

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable

class XAsiatAlbums : HttpSource() {

    override val baseUrl = "https://www.xasiat.com"
    override val lang = "all"
    override val name = "XAsiat Albums"
    override val supportsLatest = true

    private val categories = initialCategories.toMutableMap()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "XMLHttpRequest")

    override fun popularMangaRequest(page: Int): Request = searchQuery(
        path = "albums/",
        blockId = "list_albums_common_albums_list",
        page = page,
        params = mapOf("sort_by" to "album_viewed_week"),
    )

    override fun latestUpdatesRequest(page: Int): Request = searchQuery(
        path = "albums/",
        blockId = "list_albums_common_albums_list",
        page = page,
        params = mapOf("sort_by" to "post_date"),
    )

    private fun searchQuery(
        path: String,
        blockId: String,
        page: Int,
        params: Map<String, String>,
    ): Request {
        val offset = ((page - 1) * 12) + 1

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments(path.removePrefix("/").removeSuffix("/"))
            addQueryParameter("mode", "async")
            addQueryParameter("function", "get_block")
            addQueryParameter("block_id", blockId)
            addQueryParameter("from", offset.toString())

            if (blockId.contains("search")) {
                addQueryParameter("from_albums", offset.toString())
            }

            params.forEach { (key, value) ->
                addQueryParameter(key, value)
            }

            addQueryParameter("_", System.currentTimeMillis().toString())
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".list-albums .item a[href]")
            .mapNotNull { link ->
                val url = link.attr("abs:href")
                if (url.isBlank() || !url.contains("/albums/")) {
                    return@mapNotNull null
                }

                SManga.create().apply {
                    setUrlWithoutDomain(url)

                    title = link.attr("title").ifBlank {
                        link.selectFirst("img")?.attr("alt").orEmpty()
                    }

                    thumbnail_url = link.selectFirst("img")?.let { img ->
                        img.attr("data-original").ifBlank {
                            img.attr("abs:src")
                        }
                    }

                    status = SManga.COMPLETED
                    update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                }
            }
            .distinctBy { it.url }

        val hasNextPage = document.selectFirst(".load-more") != null || mangas.size >= 12

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val categoryFilter = filters.firstInstanceOrNull<UriPartFilter>()

        return when {
            query.isNotBlank() -> searchQuery(
                path = "search/search/",
                blockId = "list_albums_albums_list_search_result",
                page = page,
                params = mapOf("q" to query),
            )

            categoryFilter != null && categoryFilter.state > 0 -> searchQuery(
                path = categoryFilter.toUriPart(),
                blockId = "list_albums_common_albums_list",
                page = page,
                params = emptyMap(),
            )

            else -> latestUpdatesRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".entry-title")?.text().orEmpty()
            description = document.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            genre = getTags(document).joinToString(", ")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    private fun getTags(document: Document): List<String> {
        return document.select(".info-content a").mapNotNull { a ->
            val tag = a.text().trim()
            val href = a.attr("abs:href")

            if (tag.isNotBlank() && href.contains("/albums/")) {
                val link = href.substringAfter(".com/").removeSuffix("/")

                if (link.isNotBlank()) {
                    categories[tag] = link
                }

                tag
            } else {
                null
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url.toString()

        return listOf(
            SChapter.create().apply {
                this.url = url.removePrefix(baseUrl)
                name = "Photobook"
                date_upload = System.currentTimeMillis()
            },
        )
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chapterUrl = baseUrl + chapter.url

        return client.newCall(GET(chapterUrl, headers))
            .asObservableSuccess()
            .flatMap { response ->
                val document = response.asJsoup()

                val pageUrls = document.select(".pagination a[href], .pages a[href]")
                    .map { it.attr("abs:href") }
                    .filter { it.isNotBlank() && it.startsWith("http") }
                    .distinct()

                val allUrls = (listOf(chapterUrl) + pageUrls).distinct()

                Observable.from(allUrls)
                    .concatMap { url ->
                        client.newCall(GET(url, headers)).asObservableSuccess()
                    }
                    .toList()
                    .toObservable()
                    .map { responses ->
                        responses.flatMap { res ->
                            parseImagePages(res.asJsoup())
                        }
                            .distinct()
                            .mapIndexed { index, imageUrl ->
                                Page(index, imageUrl = imageUrl)
                            }
                    }
            }
    }

    override fun pageListParse(response: Response): List<Page> {
        return parseImagePages(response.asJsoup())
            .distinct()
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    private fun parseImagePages(document: Document): List<String> {
        return document.select("a.item[href]")
            .map { it.attr("abs:href") }
            .filter {
                it.isNotBlank() && (
                    it.contains("/get_image/") ||
                        it.endsWith(".jpg") ||
                        it.endsWith(".jpeg") ||
                        it.endsWith(".png") ||
                        it.endsWith(".webp")
                    )
            }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun getFilterList(): FilterList {
        val pairList = categories.map { (name, value) -> name to value }
            .distinctBy { it.first }
            .sortedBy { it.first.lowercase() }
            .toTypedArray()

        return FilterList(
            Filter.Header("Tags update dynamically after opening albums"),
            Filter.Separator(),
            UriPartFilter("Category", pairList),
        )
    }
}