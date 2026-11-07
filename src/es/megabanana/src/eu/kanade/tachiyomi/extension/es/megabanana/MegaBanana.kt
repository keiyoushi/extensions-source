package eu.kanade.tachiyomi.extension.es.megabanana

import eu.kanade.tachiyomi.network.HttpException
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MegaBanana : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host

    private val apiUrl get() = "$baseUrl/wp-json"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == host && it.encodedPath.startsWith("/wp-json/") }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override val supportsLatest = false

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Next catalog page and the listing it belongs to: the catalog mixes reader comics with news and PDF-only posts
    private var nextCatalogPage: Pair<HttpUrl, Int>? = null

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/megabanana/v1/catalog".toHttpUrl().newBuilder().apply {
            addQueryParameter("per_page", "48")
            filters.firstInstanceOrNull<CategoryFilter>()?.slug?.let { addQueryParameter("category", it) }
            if (query.isNotBlank()) addQueryParameter("search", query.trim())
        }.build()

        var catalogPage = if (page == 1) 1 else nextCatalogPage?.takeIf { it.first == url }?.second ?: return MangasPage(emptyList(), false)
        var mangas: List<SManga>
        var lastPage: Int
        do {
            val catalog = client.get(
                url.newBuilder().addQueryParameter("page", catalogPage.toString()).build(),
                Fetch.XHR.from("/megabanana-home/"),
            ).parseAs<CatalogDto>()
            mangas = catalog.items.filter { it.readerReady }.map { it.toSManga() }
            lastPage = catalog.pages
            catalogPage++
        } while (mangas.isEmpty() && catalogPage <= lastPage)

        nextCatalogPage = url to catalogPage
        return MangasPage(mangas, catalogPage <= lastPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != host) return null
        val segment = url.pathSegments.filter { it.isNotEmpty() }.singleOrNull() ?: return null

        val postsUrl = "$apiUrl/wp/v2/posts".toHttpUrl().newBuilder()
            .apply {
                if (segment == "index.php") {
                    val id = url.queryParameter("p")?.toIntOrNull() ?: return null
                    addQueryParameter("include", id.toString())
                } else {
                    addQueryParameter("slug", segment)
                }
            }
            .addQueryParameter("_embed", "wp:featuredmedia,wp:term")
            .addQueryParameter("_fields", "id,title,content,_links,_embedded")
            .build()
        val post = client.get(postsUrl, Fetch.XHR).parseAs<List<PostDto>>().singleOrNull() ?: return null

        // Posts without the site's reader (news, Google Drive PDFs) have no reader data
        val readerResponse = client.get(readerUrl(post.id.toString()), Fetch.XHR.from(url.toString()), ensureSuccess = false)
        if (!readerResponse.isSuccessful) {
            readerResponse.close()
            if (readerResponse.code == 404) return null
            throw HttpException(readerResponse.code)
        }

        return post.toSManga(baseUrl, readerResponse.parseAs<ReaderDto>()).apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/index.php".toHttpUrl().newBuilder()
        .addQueryParameter("p", manga.url)
        .build()
        .toString()

    override fun getChapterUrl(chapter: SChapter): String {
        val (postId, number) = chapter.url.split("/")
        return "$baseUrl/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("p", postId)
            .addQueryParameter("capitulo", number)
            .build()
            .toString()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val post = if (fetchDetails) {
            async {
                val url = "$apiUrl/wp/v2/posts/${manga.url}".toHttpUrl().newBuilder()
                    .addQueryParameter("_embed", "wp:featuredmedia,wp:term")
                    .addQueryParameter("_fields", "id,title,content,_links,_embedded")
                    .build()
                client.get(url, Fetch.XHR).parseAs<PostDto>()
            }
        } else {
            null
        }
        val reader = client.get(readerUrl(manga.url), Fetch.XHR.from(getMangaUrl(manga))).parseAs<ReaderDto>()

        SMangaUpdate(
            post?.await()?.toSManga(baseUrl, reader) ?: manga,
            reader.chapters.map { it.toSChapter(manga.url) }.reversed(),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (postId, number) = chapter.url.split("/")
        val reader = client.get(readerUrl(postId, number), Fetch.XHR.from(getChapterUrl(chapter))).parseAs<ReaderDto>()
        // The site answers an unknown chapter number with its first chapter
        check(reader.currentChapter.toString() == number) { "Chapter not found, refresh the chapter list" }
        return reader.pages.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private fun readerUrl(postId: String, number: String? = null) = "$apiUrl/megabanana/v1/reader".toHttpUrl().newBuilder()
        .addPathSegment(postId)
        .apply { number?.let { addQueryParameter("chapter", it) } }
        .build()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/megabanana/v1/navigation", Fetch.XHR.from("/megabanana-home/"))
        .parseAs<NavigationDto>()
        .items
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<CategoryDto>>() ?: return FilterList()
        // The site's "Cómics" menu: the comics category and its subcategories
        val comics = categories.first { it.slug == "comics" }
        val options = listOf("Todas" to "") +
            categories.filter { it.id == comics.id || it.parent == comics.id }.map { it.name to it.slug }
        return FilterList(CategoryFilter(options))
    }
}
