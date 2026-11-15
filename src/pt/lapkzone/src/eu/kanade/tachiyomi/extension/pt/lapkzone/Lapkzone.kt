package eu.kanade.tachiyomi.extension.pt.lapkzone

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class Lapkzone : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == host }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val listUrl = if (page == 1) "$baseUrl/mais-vistos/" else "$baseUrl/mais-vistos/page/$page/"
        val document = client.get(listUrl, Fetch.DOCUMENT).asJsoup()
        // Page 1 starts with the pinned posts, which are ads that redirect to another site
        val pinned = if (page == 1) client.get(postsUrl(1, sticky = true), Fetch.XHR).parseAs<List<PostDto>>().map { it.slug } else emptyList()
        val mangas = document.select(".lista .thumb-conteudo > a")
            .map { link ->
                SManga.create().apply {
                    url = link.absUrl("href").toHttpUrl().pathSegments.first()
                    title = link.selectFirst(".thumb-titulo")!!.text().cleanTitle()
                    thumbnail_url = link.selectFirst("img")?.absUrl("src")
                }
            }
            .filterNot { it.url in pinned }

        return MangasPage(mangas, document.selectFirst(".paginacao .next a") != null)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchPosts(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
        val included = categoryFilter?.included.orEmpty()
        val excluded = categoryFilter?.excluded.orEmpty()

        return fetchPosts(page) {
            if (query.isNotBlank()) {
                addQueryParameter("search", query.trim())
                addQueryParameter("orderby", "relevance")
            }
            if (included.isNotEmpty()) {
                addQueryParameter("categories[terms]", included.joinToString(","))
                addQueryParameter("categories[operator]", "AND")
            }
            if (excluded.isNotEmpty()) {
                addQueryParameter("categories_exclude", excluded.joinToString(","))
            }
        }
    }

    private suspend fun fetchPosts(page: Int, block: HttpUrl.Builder.() -> Unit = {}): MangasPage {
        val response = client.get(postsUrl(page, sticky = false, block), Fetch.XHR)
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 0
        val mangas = response.parseAs<List<PostDto>>().map { it.toSManga() }

        return MangasPage(mangas, page < totalPages)
    }

    private fun postsUrl(page: Int, sticky: Boolean, block: HttpUrl.Builder.() -> Unit = {}) = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder().apply {
        addQueryParameter("page", page.toString())
        addQueryParameter("per_page", "40")
        addQueryParameter("sticky", sticky.toString())
        addQueryParameter("_fields", "slug,title,yoast_head_json.og_image")
        block()
    }.build()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != host) return null
        val slug = url.pathSegments.filter { it.isNotEmpty() }.singleOrNull() ?: return null

        val manga = SManga.create().apply { this.url = slug }
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        // Site pages like /categorias/ have no post title
        if (document.selectFirst("#lapk-post-title") == null) return null

        return document.toSManga(slug).apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/${manga.url}/"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        // Posts tagged TUFOS are a few preview pages of a comic sold on tufos.com.br
        val isPreview = document.selectFirst(".lapk-post-terms a[href*=/tag/tufos/]") != null
        val chapter = SChapter.create().apply {
            url = manga.url
            name = if (isPreview) "Prévia" else "Capítulo Único"
            date_upload = Instant.tryParse(document.selectFirst(".lapk-post-published time")?.attrOrNull("datetime"))
        }

        return SMangaUpdate(document.toSManga(manga.url), listOf(chapter))
    }

    private fun Document.toSManga(slug: String) = SManga.create().apply {
        url = slug
        title = selectFirst("#lapk-post-title")!!.text().cleanTitle()
        description = select(".lapk-post-intro p").eachText()
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
            .ifEmpty { null }
        genre = select(".lapk-post-terms a").eachText()
            .distinctBy { it.lowercase() }
            .joinToString()
            .ifEmpty { null }
        thumbnail_url = selectFirst("meta[property=og:image]")?.attrOrNull("content")
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".post-texto img")
        // Previews end with a banner that links to the store selling the comic
        .filterNot { it.linksElsewhere() }
        .mapIndexed { index, img -> Page(index, imageUrl = img.largestImageUrl()) }

    private fun Element.linksElsewhere(): Boolean {
        val link = closest("a")?.absUrl("href")?.toHttpUrlOrNull() ?: return false
        return link.host != host
    }

    // The src is a resized copy; the original is the widest srcset entry
    private fun Element.largestImageUrl(): String = attr("srcset").split(",")
        .map { it.trim().split(" ") }
        .filter { it.size == 2 }
        .maxByOrNull { it[1].removeSuffix("w").toIntOrNull() ?: 0 }
        ?.let { baseUrl.toHttpUrl().resolve(it[0])!!.toString() }
        ?: absUrl("src")

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/wp-json/wp/v2/categories?per_page=100&_fields=id,name,count", Fetch.XHR)
        .parseAs<List<CategoryDto>>()
        .filter { it.count > 0 }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<CategoryDto>>() ?: return FilterList()
        return FilterList(CategoryFilter(categories))
    }
}

// Most titles end with a dash and the label "HQ HENTAI +18" or a variant of it
private val TITLE_LABEL = Regex("""\s+\p{Pd}\s+[\p{Lu}\s]+\+18$""")

internal fun String.cleanTitle() = replace(TITLE_LABEL, "")
