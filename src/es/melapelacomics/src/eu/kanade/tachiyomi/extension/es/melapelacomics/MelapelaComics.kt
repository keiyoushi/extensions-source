package eu.kanade.tachiyomi.extension.es.melapelacomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getArrayOrNull
import keiyoushi.utils.string
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class MelapelaComics : KeiSource() {

    // The site only lists its posts by date
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = getPostList(listUrl(page = page))

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selected
        if (query.isBlank()) return getPostList(listUrl(category?.let { "category/$it" }, page))

        val url = listUrl(page = page).newBuilder()
            .addQueryParameter("s", query.trim())
            .apply { category?.let { addQueryParameter("category_name", it) } }
            .build()
        val response = client.get(url, Fetch.DOCUMENT)
        // A search without results redirects to the home page
        if (response.request.url.queryParameter("s") == null) {
            response.close()
            return MangasPage(emptyList(), false)
        }
        return parsePostList(response.asJsoup())
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (url.host.removePrefix("www.") == baseUrl.toHttpUrl().host && segments.size >= 2 && segments[0] in LIST_PATHS) {
            return getPostList(listUrl("${segments[0]}/${segments[1]}", page))
        }
        return super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.singleOrNull(String::isNotEmpty) ?: return null

        val document = client.get(listUrl(slug, page = 1), Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(POST_SELECTOR) == null) return null
        return parseDetails(document).apply {
            this.url = "/$slug/"
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            date_upload = Instant.tryParse(document.selectFirst("$POST_SELECTOR time.published")?.attr("datetime"))
            memo = buildJsonObject { putJsonArray("pages") { parsePages(document).forEach { add(it) } } }
        }
        return SMangaUpdate(parseDetails(document).apply { url = manga.url }, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = chapter.memo.getArrayOrNull("pages")?.map { it.string }
            ?: parsePages(client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup())
        return pages.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private fun listUrl(path: String? = null, page: Int): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .apply {
            path?.let(::addPathSegments)
            if (page > 1) addPathSegments("page/$page")
        }
        .addPathSegment("")
        .build()

    private suspend fun getPostList(url: HttpUrl): MangasPage = parsePostList(client.get(url, Fetch.DOCUMENT).asJsoup())

    private fun parsePostList(document: Document): MangasPage {
        val mangas = document.select("#loop-content .c-blog_item").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(".post-title a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = element.selectFirst(".c-blog__thumbnail img")?.attrOrNull("abs:data-src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a.nextpostslink") != null)
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("$POST_SELECTOR h2.item-title")!!.text()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attrOrNull("content")
        genre = document.select("$POST_SELECTOR .post-category a, .item-tags a[rel=tag]")
            .map { it.text() }
            .distinct()
            .joinToString()
            .ifEmpty { null }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    // Older posts link their pages over http, which browsers upgrade to https
    private fun parsePages(document: Document): List<String> = document.select("$POST_SELECTOR .entry-content_wrap img:not(.rel-thumb)")
        .map { it.absUrl("src").toHttpUrl().newBuilder().scheme("https").build().toString() }

    companion object {
        private const val POST_SELECTOR = ".c-blog-post"
        private val LIST_PATHS = setOf("category", "tag")
    }
}
