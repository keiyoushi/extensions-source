package eu.kanade.tachiyomi.extension.all.gaoqingpao

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Source
abstract class Gaoqingpao : KeiSource() {

    // Every listing is served in a random order.
    override val supportsLatest = false

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    private val apiUrl get() = "$baseUrl/wp-json/wp/v2".toHttpUrl()

    // Post content links images by path; the site's pages load them from the img host.
    private val imageBaseUrl get() = "https://img.${baseUrl.toHttpUrl().topPrivateDomain()}/"

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getPosts(page) {
        if (query.isNotEmpty()) {
            addQueryParameter("search", query)
            // Post content is only image tags.
            addQueryParameter("search_columns", "post_title")
        }
        filters.firstInstanceOrNull<CategoryFilter>()?.id?.let { addQueryParameter("categories", it.toString()) }
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        if (POST_PATH.matches(url.encodedPath)) return super.getMangasByUrl(url, page)
        val segments = url.encodedPathSegments.filter { it.isNotEmpty() }.let {
            if (it.size > 2 && it[it.size - 2] == "page") it.dropLast(2) else it
        }
        return when {
            segments.size == 2 && segments[0] == "tag" -> getTagPage(segments[1], page)
            segments.size == 1 -> getCategoryPage(segments[0], page)
            else -> MangasPage(emptyList(), false)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = POST_PATH.matchEntire(url.encodedPath)?.groupValues?.get(1) ?: return null
        return getPost(id).toSManga(imageBaseUrl).apply {
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/g${manga.url}.html"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/g${chapter.url}.html"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val post = getPost(manga.url)
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Gallery"
            date_upload = DATE_FORMAT.tryParseDateTime(post.dateGmt, ZoneOffset.UTC)
        }
        return SMangaUpdate(post.toSManga(imageBaseUrl), listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = apiUrl.newBuilder()
            .addPathSegment("posts")
            .addPathSegment(chapter.url)
            .addQueryParameter("_fields", "content")
            .build()
        return client.get(url, Fetch.XHR).parseAs<PostContentDto>()
            .content
            .imageUrls(imageBaseUrl)
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = apiUrl.newBuilder()
            .addPathSegment("tags")
            .addQueryParameter("post", manga.url)
            .addQueryParameter("_fields", "id,name,slug")
            .build()
        val tags = client.get(url, Fetch.XHR).parseAs<List<TermDto>>()
        return coroutineScope {
            tags.map { async { getTagPage(it.slug, 1).mangas } }.awaitAll()
        }.flatten().distinctBy { it.url }.filter { it.url != manga.url }
    }

    override suspend fun fetchFilterData(): JsonElement {
        val url = apiUrl.newBuilder()
            .addPathSegment("categories")
            .addQueryParameter("per_page", "100")
            .addQueryParameter("_fields", "id,name,slug")
            .build()
        return client.get(url, Fetch.XHR).parseAs<List<TermDto>>().toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<TermDto>>() ?: return FilterList()
        return FilterList(CategoryFilter(categories.map { it.name to it.id }))
    }

    private suspend fun getPosts(page: Int, query: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = apiUrl.newBuilder()
            .addPathSegment("posts")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())
            .addQueryParameter("_fields", "id,title,content")
            .apply(query)
            .build()
        val response = client.get(url, Fetch.XHR)
        // X-WP-TotalPages is wrong for searches and categories here.
        val totalPages = response.header("X-WP-TotalPages")!!.toInt()
        val posts = response.parseAs<List<PostDto>>()
        return MangasPage(posts.map { it.toSManga(imageBaseUrl) }, posts.size == PAGE_SIZE && page < totalPages)
    }

    // The REST tag filter drops posts at random; the site's tag pages list them all, newest first.
    private suspend fun getTagPage(slug: String, page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("tag")
            .addEncodedPathSegment(slug)
            .apply { if (page > 1) addPathSegment("page").addPathSegment(page.toString()) }
            .addPathSegment("")
            .build()
        val response = client.get(url, Fetch.DOCUMENT, ensureSuccess = false)
        // A page past the end of the tag is a 404.
        if (response.code == 404 && page > 1) {
            response.close()
            return MangasPage(emptyList(), false)
        }
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
        val document = response.asJsoup()
        val mangas = document.select("li.item").map { item ->
            val link = item.selectFirst("h2.item-title a")!!
            SManga.create().apply {
                this.url = POST_PATH.matchEntire(link.absUrl("href").toHttpUrl().encodedPath)!!.groupValues[1]
                title = link.text()
                thumbnail_url = item.selectFirst(".item-img img")?.absUrl("src")
            }
        }
        // Past page 50 the pager links a next page even on the last one.
        val hasNextPage = document.selectFirst(".pagination a.next") != null && mangas.size == TAG_PAGE_SIZE
        return MangasPage(mangas, hasNextPage)
    }

    private suspend fun getCategoryPage(slug: String, page: Int): MangasPage {
        val url = apiUrl.newBuilder()
            .addPathSegment("categories")
            .addEncodedQueryParameter("slug", slug)
            .addQueryParameter("_fields", "id,name,slug")
            .build()
        val category = client.get(url, Fetch.XHR).parseAs<List<TermDto>>().firstOrNull()
            ?: return MangasPage(emptyList(), false)
        return getPosts(page) { addQueryParameter("categories", category.id.toString()) }
    }

    private suspend fun getPost(id: String): PostDto {
        val url = apiUrl.newBuilder()
            .addPathSegment("posts")
            .addPathSegment(id)
            .addQueryParameter("_embed", "wp:term")
            // _embed only applies when its _links entry is among the fields.
            .addQueryParameter("_fields", "id,title,content,date_gmt,_links.wp:term,_embedded")
            .build()
        return client.get(url, Fetch.XHR).parseAs()
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val TAG_PAGE_SIZE = 10
        private val POST_PATH = """/g(\d+)\.html/?""".toRegex()
        private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
