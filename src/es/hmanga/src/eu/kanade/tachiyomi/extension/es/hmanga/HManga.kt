package eu.kanade.tachiyomi.extension.es.hmanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.parser.Parser

@Source
abstract class HManga : KeiSource() {

    private val apiUrl get() = "$baseUrl/wp-json"

    // Views over the last week, as in the site's "Mas Popular" box
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/wordpress-popular-posts/v1/popular-posts".toHttpUrl().newBuilder()
            .addQueryParameter("range", "last7days")
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .addListFields()
            .build()
        val posts = client.get(url, Fetch.XHR).parseAs<List<Post>>()
        return MangasPage(posts.map { it.toSManga() }, posts.size == PAGE_SIZE)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPosts(page) {}

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tags = filters.firstInstanceOrNull<TagFilter>()
        return getPosts(page) {
            if (query.isNotBlank()) {
                addQueryParameter("search", query.trim())
                addQueryParameter("orderby", "relevance")
            }
            tags?.included?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("categories[terms]", it.joinToString(","))
                addQueryParameter("categories[operator]", "AND")
            }
            tags?.excluded?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("categories_exclude", it.joinToString(","))
            }
        }
    }

    private suspend fun getPosts(page: Int, query: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$apiUrl/wp/v2/posts".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())
            .apply(query)
            .addListFields()
            .build()
        val response = client.get(url, Fetch.XHR)
        val totalPages = response.header("X-WP-TotalPages")!!.toInt()
        val posts = response.parseAs<List<Post>>()
        return MangasPage(posts.map { it.toSManga() }, page < totalPages)
    }

    private fun HttpUrl.Builder.addListFields() = addQueryParameter("_fields", "slug,title,_links,_embedded")
        .addQueryParameter("_embed", "wp:featuredmedia")

    // Author, tag and series pages list their posts
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val taxonomy = TAXONOMIES[segments.firstOrNull()]
        if (taxonomy == null || segments.size < 2 || !url.isSite()) return super.getMangasByUrl(url, page)
        val termUrl = "$apiUrl/wp/v2/$taxonomy".toHttpUrl().newBuilder()
            .addQueryParameter("slug", segments[1])
            .addQueryParameter("_fields", "id")
            .build()
        val term = client.get(termUrl, Fetch.XHR).parseAs<List<TermId>>().firstOrNull()
            ?: return MangasPage(emptyList(), false)
        return getPosts(page) { addQueryParameter(taxonomy, term.id.toString()) }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 1 || !url.isSite()) return null
        return getPost(segments[0], DETAILS_FIELDS, DETAILS_EMBED)?.toSMangaDetails()
            ?.apply { initialized = true }
    }

    private fun HttpUrl.isSite() = host.removePrefix("www.") == baseUrl.toHttpUrl().host

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val post = getPost(manga.url.trim('/'), DETAILS_FIELDS, DETAILS_EMBED)!!
        return SMangaUpdate(post.toSMangaDetails(), listOf(post.toSChapter()))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = getPost(chapter.url.trim('/'), "slug,title,content", null)!!
        .pageUrls()
        .mapIndexed { index, url -> Page(index, imageUrl = url) }

    private suspend fun getPost(slug: String, fields: String, embed: String?): Post? {
        val url = "$apiUrl/wp/v2/posts".toHttpUrl().newBuilder()
            .addQueryParameter("slug", slug)
            .addQueryParameter("_fields", fields)
            .apply { embed?.let { addQueryParameter("_embed", it) } }
            .build()
        return client.get(url, Fetch.XHR).parseAs<List<Post>>().firstOrNull()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val tags = mutableListOf<Tag>()
        var page = 0
        do {
            val url = "$apiUrl/wp/v2/categories".toHttpUrl().newBuilder()
                .addQueryParameter("per_page", "100")
                .addQueryParameter("page", (++page).toString())
                .addQueryParameter("hide_empty", "true")
                .addQueryParameter("_fields", "id,name")
                .build()
            val response = client.get(url, Fetch.XHR)
            val totalPages = response.header("X-WP-TotalPages")!!.toInt()
            tags += response.parseAs<List<Tag>>()
        } while (page < totalPages)
        return tags.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<Tag>>() ?: return FilterList()
        return FilterList(TagFilter(tags.map { TagState(it.id, Parser.unescapeEntities(it.name, false)) }))
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val DETAILS_FIELDS = "slug,title,date_gmt,_links,_embedded"
        private const val DETAILS_EMBED = "wp:featuredmedia,wp:term"
        private val TAXONOMIES = mapOf("autor" to "tags", "tags" to "categories", "series" to "series")
    }
}
