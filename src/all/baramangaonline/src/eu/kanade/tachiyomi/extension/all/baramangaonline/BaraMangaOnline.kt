package eu.kanade.tachiyomi.extension.all.baramangaonline

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class BaraMangaOnline : KeiSource() {

    // The site lists its posts newest first, with no other order
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val categories = listOfNotNull(
            filters.firstInstanceOrNull<LanguageFilter>()?.id,
            filters.firstInstanceOrNull<TypeFilter>()?.id,
        )
        return getPosts(page) {
            if (query.isNotBlank()) addQueryParameter("search", query.trim())
            if (categories.isNotEmpty()) {
                addQueryParameter("categories[terms]", categories.joinToString(","))
                addQueryParameter("categories[operator]", "AND")
            }
        }
    }

    // The home page, the search and the category lists (languages, artists, circles...), from any of their pages
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!isSiteUrl(url)) return super.getMangasByUrl(url, page)
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val pageIndex = segments.indexOf("page")
        val path = if (pageIndex == -1) segments else segments.take(pageIndex)
        val firstPage = if (pageIndex == -1) 1 else segments.getOrNull(pageIndex + 1)?.toIntOrNull() ?: 1
        val listPage = firstPage + page - 1

        return when {
            path.isEmpty() && url.queryParameter("p") == null -> getPosts(listPage) {
                url.queryParameter("s")?.takeIf { it.isNotBlank() }?.let { addQueryParameter("search", it.trim()) }
            }
            path.size >= 2 && path[0] == "category" -> {
                val categoryId = categoryIds[path.last()] ?: client.get(
                    apiUrl("wp/v2/categories") {
                        addQueryParameter("slug", path.last())
                        addQueryParameter("_fields", "id")
                    },
                    Fetch.XHR,
                ).parseAs<List<CategoryDto>>().firstOrNull()?.id?.also { categoryIds[path.last()] = it }
                    ?: return MangasPage(emptyList(), false)
                getPosts(listPage) {
                    addQueryParameter("categories[terms]", categoryId.toString())
                    addQueryParameter("categories[include_children]", "true")
                }
            }
            else -> super.getMangasByUrl(url, page)
        }
    }

    // A category list asks for its id once, not for each of its pages
    private val categoryIds = ConcurrentHashMap<String, Int>()

    // Post links, by their slug or as WordPress short links (?p=<id>)
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!isSiteUrl(url)) return null
        val id = url.queryParameter("p")?.toIntOrNull()
        val slug = url.pathSegments.filter { it.isNotEmpty() }.singleOrNull()
        val post = when {
            id != null -> client.get(detailsUrl(id.toString()), Fetch.XHR).parseAs<PostDetailsDto>()
            slug != null -> client.get(
                apiUrl("wp/v2/posts") {
                    addQueryParameter("slug", slug)
                    addDetailsParameters()
                },
                Fetch.XHR,
            ).parseAs<List<PostDetailsDto>>().firstOrNull()
            else -> null
        } ?: return null

        return post.toSManga().apply { initialized = true }
    }

    // Details and the only chapter come from the same post
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val post = client.get(detailsUrl(manga.url), Fetch.XHR).parseAs<PostDetailsDto>()
        return SMangaUpdate(post.toSManga(), listOf(post.toSChapter()))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(
        apiUrl("wp/v2/posts/${chapter.url}") { addQueryParameter("_fields", "content") },
        Fetch.XHR,
    ).parseAs<PostContentDto>().content.rendered
        .asJsoup(baseUrl)
        .select("img")
        .mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("src")) }

    override val supportsRelatedMangas = true

    // The site's related posts (YARPP)
    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(
        apiUrl("yarpp/v1/related/${manga.url}") { addQueryParameter("_fields", LIST_FIELDS) },
        Fetch.XHR,
    ).parseAs<List<PostDto>>()
        .filter { post -> post.categories.none { it in EXCLUDED_CATEGORIES } }
        .map { it.toSManga() }

    override fun getFilterList(data: JsonElement?) = getFilters()

    // WordPress sends a post's id to its address
    override fun getMangaUrl(manga: SManga) = "$baseUrl/?p=${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/?p=${chapter.url}"

    private suspend fun getPosts(page: Int, listParameters: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = apiUrl("wp/v2/posts") {
            addQueryParameter("per_page", "24")
            addQueryParameter("page", page.toString())
            addQueryParameter("categories_exclude", EXCLUDED_CATEGORIES.joinToString(","))
            addQueryParameter("_fields", LIST_FIELDS)
            listParameters()
        }
        val response = client.get(url, Fetch.XHR)
        // The API answers 400 past the last page
        val totalPages = response.header("X-WP-TotalPages")!!.toInt()
        val mangas = response.parseAs<List<PostDto>>().map { it.toSManga() }
        return MangasPage(mangas, page < totalPages)
    }

    private fun detailsUrl(id: String) = apiUrl("wp/v2/posts/$id") { addDetailsParameters() }

    private fun HttpUrl.Builder.addDetailsParameters() {
        addQueryParameter("_embed", "wp:term")
        addQueryParameter("_fields", DETAILS_FIELDS)
    }

    private fun apiUrl(path: String, parameters: HttpUrl.Builder.() -> Unit) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("wp-json")
        .addPathSegments(path)
        .apply(parameters)
        .build()

    private fun isSiteUrl(url: HttpUrl) = url.host.removePrefix("www.") == baseUrl.toHttpUrl().host

    companion object {
        private const val LIST_FIELDS = "id,title,categories,yoast_head_json.og_image"
        private const val DETAILS_FIELDS = "id,title,date_gmt,yoast_head_json.og_image,_links.wp:term,_embedded"

        // Games & Applications, Animation and Gay Cinema: games and videos, with nothing to read
        private val EXCLUDED_CATEGORIES = listOf(115, 146, 345)
    }
}
