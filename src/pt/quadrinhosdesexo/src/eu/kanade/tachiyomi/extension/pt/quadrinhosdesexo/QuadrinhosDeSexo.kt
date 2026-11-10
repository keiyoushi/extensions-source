package eu.kanade.tachiyomi.extension.pt.quadrinhosdesexo

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class QuadrinhosDeSexo : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == host }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override val supportsLatest = false

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
        val included = categoryFilter?.included.orEmpty()
        val excluded = categoryFilter?.excluded.orEmpty() + HIDDEN_CATEGORIES

        val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "40")
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
                addQueryParameter("orderby", "relevance")
            } else {
                addQueryParameter("orderby", "modified")
            }
            if (included.isNotEmpty()) {
                addQueryParameter("categories[terms]", included.joinToString(","))
                addQueryParameter("categories[operator]", "AND")
            }
            addQueryParameter("categories_exclude", excluded.joinToString(","))
            addQueryParameter("_fields", "slug,link,title,yoast_head_json.og_image")
        }.build()

        val response = client.get(url, Fetch.XHR)
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 0
        val mangas = response.parseAs<List<PostDto>>()
            // Ad posts link off-site
            .filter { it.link.toHttpUrl().host == host }
            .map { it.toSManga() }

        return MangasPage(mangas, page < totalPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != host) return null
        val slug = url.pathSegments.filter { it.isNotEmpty() }.singleOrNull() ?: return null

        val manga = SManga.create().apply { this.url = slug }
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        // Only posts have a publish date; site pages like /contato/ don't
        if (document.selectFirst("meta[property=article:published_time]") == null) return null

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

        // Area Vip posts are a few preview pages of a comic sold elsewhere
        val isPreview = document.selectFirst(".cn-links a.cat[href*=/category/area-vip/]") != null
        val chapter = SChapter.create().apply {
            url = manga.url
            name = if (isPreview) "Prévia" else "Capítulo Único"
            date_upload = Instant.tryParse(document.selectFirst("meta[property=article:published_time]")?.attrOrNull("content"))
        }

        return SMangaUpdate(document.toSManga(manga.url), listOf(chapter))
    }

    private fun Document.toSManga(slug: String) = SManga.create().apply {
        url = slug
        title = selectFirst(".cn-article h1")!!.text()
        genre = select(".cn-links a.cat").joinToString { it.text() }.ifEmpty { null }
        description = select(".cn-texts > p").map { it.text() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
            .ifEmpty { null }
        thumbnail_url = selectFirst("meta[property=og:image]")?.attrOrNull("content")
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".cn-texts .gallery img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.largestImageUrl()) }

    // Older galleries show a resized copy in src; the original is the widest srcset entry
    private fun Element.largestImageUrl(): String = attr("srcset").split(",")
        .map { it.trim().split(" ") }
        .filter { it.size == 2 }
        .maxByOrNull { it[1].removeSuffix("w").toIntOrNull() ?: 0 }
        ?.let { baseUrl.toHttpUrl().resolve(it[0])!!.toString() }
        ?: absUrl("src")

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        fun url(page: Int) = "$baseUrl/wp-json/wp/v2/categories".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", "100")
            .addQueryParameter("_fields", "id,name,count")
            .build()

        val response = client.get(url(1), Fetch.XHR)
        val totalPages = response.header("X-WP-TotalPages")!!.toInt()
        val categories = response.parseAs<List<CategoryDto>>() +
            (2..totalPages).map { async { client.get(url(it), Fetch.XHR).parseAs<List<CategoryDto>>() } }.awaitAll().flatten()

        categories
            .filter { it.count > 0 && it.id !in HIDDEN_CATEGORIES }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<CategoryDto>>() ?: return FilterList()
        return FilterList(CategoryFilter(categories))
    }

    companion object {
        // Famosas Nuas, Fotos Porno, Acompanhantes, Videos Hentai, Jav Legendado: photo and video posts
        private val HIDDEN_CATEGORIES = listOf(1107, 661, 1292, 2360, 3387)
    }
}
