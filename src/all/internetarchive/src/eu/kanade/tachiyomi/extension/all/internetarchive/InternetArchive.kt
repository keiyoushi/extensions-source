package eu.kanade.tachiyomi.extension.all.internetarchive

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class InternetArchive : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // Item metadata comes without caching headers; details and page lists read the same item
            if (!response.isSuccessful || chain.request().url.pathSegments.first() != "metadata") return@addNetworkInterceptor response
            response.newBuilder()
                .header("Cache-Control", "max-age=600")
                .build()
        }

    override suspend fun getPopularManga(page: Int) = searchPage(page, "", getFilterList())

    override suspend fun getLatestUpdates(page: Int) = searchPage(page, "", FilterList(CollectionFilter(), SortFilter("addeddate")))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = searchPage(page, query, filters)

    private suspend fun searchPage(page: Int, query: String, filters: FilterList, collection: String? = null): MangasPage {
        val terms = buildList {
            if (query.isNotBlank()) add("(${query.trim()})")
            (collection ?: filters.firstInstance<CollectionFilter>().collection)?.let { add("collection:($it)") }
            filters.filterIsInstance<FieldFilter>().filter { it.state.isNotBlank() }.forEach { add("${it.field}:(${it.state.trim()})") }
            add("mediatype:(texts OR image)")
            add("NOT access-restricted-item:true")
        }
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("advancedsearch.php")
            .addQueryParameter("q", terms.joinToString(" AND "))
            .addQueryParameter("fl[]", "identifier")
            .addQueryParameter("fl[]", "title")
            .apply { filters.firstInstance<SortFilter>().sort?.let { addQueryParameter("sort[]", it) } }
            .addQueryParameter("rows", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("output", "json")
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<SearchDto>().response

        return MangasPage(
            result.docs.mapNotNull { it.toSManga(baseUrl) },
            page * PAGE_SIZE < minOf(result.numFound, MAX_RESULTS),
        )
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] !in URL_PATHS || segments[1].isEmpty()) return MangasPage(emptyList(), false)

        val item = getItem(segments[1])
        val metadata = item.metadata ?: return MangasPage(emptyList(), false)
        if (metadata.mediatype == "collection") {
            return searchPage(page, "", getFilterList(), metadata.identifier)
        }

        val manga = item.toSManga(SManga.create(), baseUrl).apply { initialized = true }
        return MangasPage(listOf(manga), false)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/details/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val item = getItem(manga.url)
        if (item.metadata == null) throw Exception(REMOVED_ITEM)
        return SMangaUpdate(item.toSManga(manga, baseUrl), item.toChapters(baseUrl))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = getChapterUrl(chapter).toHttpUrl().pathSegments
        val identifier = segments[1]
        val path = segments.drop(2).joinToString("/")
        val item = getItem(identifier)
        val server = item.server ?: throw Exception(REMOVED_ITEM)
        val serverUrl = HttpUrl.Builder().scheme("https").host(server).build()
        val itemPath = item.dir!!

        if (segments[0] == "details") {
            val url = serverUrl.newBuilder()
                .addPathSegments("BookReader/BookReaderJSIA.php")
                .addQueryParameter("id", identifier)
                .addQueryParameter("itemPath", itemPath)
                .addQueryParameter("server", serverUrl.host)
                .addQueryParameter("format", "json")
                .addQueryParameter("subPrefix", path)
                .build()
            return client.get(url, Fetch.XHR).parseAs<BookReaderDto>().data.brOptions.data
                .flatten()
                .mapIndexed { index, page -> Page(index, imageUrl = page.uri) }
        }

        if (item.files.any { it.name == path }) {
            val archiveUrl = serverUrl.newBuilder()
                .addPathSegment("view_archive.php")
                .addQueryParameter("archive", "$itemPath/$path")
                .build()
            return client.get(archiveUrl, Fetch.DOCUMENT).asJsoup()
                .select("table.archext tr > td:first-child > a[href]")
                // Entry names keep their exact whitespace
                .map { it.wholeText() }
                .filter { it.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS }
                // Resource-fork stubs that macOS adds to zips
                .filterNot { "__MACOSX/" in it }
                .mapIndexed { index, entry ->
                    Page(index, imageUrl = archiveUrl.newBuilder().addQueryParameter("file", entry).build().toString())
                }
        }

        return item.files
            .filter { it.isImage && it.folder == path }
            .sortedBy { naturalKey(it.name) }
            .mapIndexed { index, file ->
                val imageUrl = serverUrl.newBuilder()
                    .addPathSegments(itemPath.removePrefix("/"))
                    .addPathSegments(file.name)
                    .build()
                Page(index, imageUrl = imageUrl.toString())
            }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CollectionFilter(),
        SortFilter(),
        Filter.Separator(),
        FieldFilter("Creator", "creator"),
        FieldFilter("Subject", "subject"),
        FieldFilter("Language", "language"),
    )

    private suspend fun getItem(identifier: String): ItemDto {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("metadata")
            .addPathSegment(identifier)
            .build()
        return client.get(url, Fetch.XHR).parseAs()
    }

    companion object {
        private const val PAGE_SIZE = 50
        private const val REMOVED_ITEM = "This item is no longer on the Internet Archive"

        // advancedsearch.php refuses pages past its deep paging limit
        private const val MAX_RESULTS = 10000

        private val URL_PATHS = setOf("details", "download", "stream")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
    }
}
