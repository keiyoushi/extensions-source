package eu.kanade.tachiyomi.extension.all.tvymanga

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException

/**
 * tvymanga3.com is a plain WordPress blog: every chapter is a post. Most series have a category, but many
 * chapters are left in "Uncategorized" (and the English ones in "INGLES"), so those are grouped by the series
 * name taken from the post title ("Kanojo, Okarishimasu 447" -> "Kanojo, Okarishimasu").
 * One source per language: `es` lists the Spanish chapters, `en` the English ones.
 */
@Source
abstract class TvyManga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::coverInterceptor)

    // ============================== Catalog ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = listPage(loadSeries(null), page, byName = false)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val byName = filters.firstInstanceOrNull<SortFilter>()?.byName == true
        return listPage(loadSeries(query.ifBlank { null }), page, byName)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("La búsqueda agrupa los capítulos por nombre de serie"),
        SortFilter(),
    )

    private class SortFilter : Filter.Select<String>("Ordenar por", arrayOf("Más capítulos", "Nombre (A-Z)")) {
        val byName get() = state == 1
    }

    private fun listPage(series: List<Series>, page: Int, byName: Boolean): MangasPage {
        val sorted = if (byName) series.sortedBy { it.title.lowercase() } else series.sortedByDescending { it.count }
        val chunk = sorted.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE)
        return MangasPage(chunk.map { it.toSManga() }, page * PAGE_SIZE < sorted.size)
    }

    private suspend fun allCategories(): SeriesIndex = SeriesIndex(fetchAll(apiUrl("categories"), "id,name,slug,count"))

    /** Every series of this source's language, or the ones matching [query]. */
    private suspend fun loadSeries(query: String?): List<Series> {
        val index = allCategories()
        val queryKey = query?.toKey().orEmpty()
        val series = LinkedHashMap<String, Series>()

        if (lang == LANG_ES) {
            index.real.map(index::categorySeries)
                .filter { queryKey.isEmpty() || queryKey in it.key }
                .forEach { series.putIfAbsent(it.key, it) }
        }

        val posts = if (lang == LANG_EN) {
            val english = index.englishId ?: return emptyList()
            fetchAll<PostDto>(postsUrl(query).addQueryParameter("categories", english.toString()), POST_FIELDS)
        } else if (queryKey.isEmpty()) {
            val loose = index.looseIds.joinToString(",").ifEmpty { return series.values.toList() }
            fetchAll<PostDto>(postsUrl(null).addQueryParameter("categories", loose), POST_FIELDS)
        } else {
            fetchAll<PostDto>(postsUrl(query), POST_FIELDS, maxPages = SEARCH_PAGES)
                .filter { index.englishId !in it.categories }
        }

        for (post in posts) {
            val found = index.seriesOf(post, lang)
            if (queryKey.isNotEmpty() && queryKey !in found.key) continue
            val known = series[found.key]
            if (known == null) {
                series[found.key] = found
            } else if (found.categoryId == null) {
                known.count++
            }
        }
        return series.values.toList()
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val index = allCategories()
        val url = postsUrl(null)
            .addQueryParameter("per_page", "50")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("_fields", POST_FIELDS)
        if (lang == LANG_EN) {
            val english = index.englishId ?: return MangasPage(emptyList(), false)
            url.addQueryParameter("categories", english.toString())
        }
        val response = client.get(url.build(), Fetch.XHR)
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 1
        val mangas = response.parseAs<List<PostDto>>()
            .filter { lang == LANG_EN || index.englishId !in it.categories }
            .map { index.seriesOf(it, lang) }
            .distinctBy { it.key }
            .map { it.toSManga() }
        return MangasPage(mangas, page < totalPages)
    }

    // =========================== Details/Chapters ==========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val posts = seriesPosts(manga)
        manga.status = SManga.UNKNOWN
        manga.description = "Capítulos: ${posts.size}"
        manga.initialized = true
        return SMangaUpdate(manga, posts.map { it.toSChapter() })
    }

    private suspend fun seriesPosts(manga: SManga): List<PostDto> {
        val index = allCategories()
        val url = (baseUrl + manga.url).toHttpUrl()
        val posts = if (url.encodedPath.startsWith("/category/")) {
            categoryPosts(index, url.encodedPath.removePrefix("/category/").trim('/'))
        } else {
            val name = url.queryParameter("s") ?: throw IOException("Serie no encontrada")
            val english = url.queryParameter(LANG_PARAM) == LANG_EN
            val search = postsUrl(name)
            if (english) index.englishId?.let { search.addQueryParameter("categories", it.toString()) }
            fetchAll<PostDto>(search, POST_FIELDS, maxPages = SEARCH_PAGES)
                .filter { it.seriesKey == name.toKey() && (english || index.englishId !in it.categories) }
        }
        return posts.distinctBy { it.id }.sortedByDescending { it.dateGmt }
    }

    private suspend fun categoryPosts(index: SeriesIndex, slug: String): List<PostDto> {
        val category = index.real.firstOrNull { it.slug == slug } ?: throw IOException("Serie no encontrada")
        val title = category.name.decodeHtml()
        val own = fetchAll<PostDto>(postsUrl(null).addQueryParameter("categories", category.id.toString()), POST_FIELDS)

        // Category names often differ from chapter titles, so also accept the name most chapters use
        val usual = own.groupingBy { it.seriesKey }.eachCount().maxByOrNull { it.value }?.key
        val accepted = setOfNotNull(title.toKey(), usual)
        // The site sometimes files a chapter under another series' category
        val elsewhere = index.categoryKeys - accepted
        val misfiled = fetchAll<PostDto>(postsUrl(title), POST_FIELDS, maxPages = SEARCH_PAGES)
            .filter { it.seriesKey in accepted }
        return own.filter { it.seriesKey !in elsewhere } + misfiled
    }

    // ================================ Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = postsUrl(null).addQueryParameter("slug", chapter.url.trim('/')).addQueryParameter("_fields", "content").build()
        val html = client.get(url, Fetch.XHR).parseAs<List<PostContentDto>>().firstOrNull()?.content?.rendered
            ?: throw IOException("Capítulo no encontrado")
        return Jsoup.parseBodyFragment(html, baseUrl).select("img")
            .filter { it.attr("width") != "0" } // 0x0 tracking pixel before the pages
            .map { img -> img.absUrl("data-src").ifEmpty { img.absUrl("src") } }
            .filter { it.isNotEmpty() }
            .distinct()
            .mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    // ================================ Covers ==============================

    // The site has no cover art: the cover is the first image of the newest chapter, found when the image is loaded
    private fun Series.toSManga() = SManga.create().apply {
        url = this@toSManga.url
        title = this@toSManga.title
        thumbnail_url = coverUrl()
    }

    private fun Series.coverUrl(): String? {
        val url = postsUrl(null).addQueryParameter("per_page", "1").addQueryParameter("_fields", "content")
        when {
            categoryId != null -> url.addQueryParameter("categories", categoryId.toString())
            newestChapterSlug != null -> url.addQueryParameter("slug", newestChapterSlug)
            else -> return null
        }
        return url.addQueryParameter(COVER_PARAM, "1").build().toString()
    }

    private fun coverInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.queryParameter(COVER_PARAM) == null) return chain.proceed(request)

        val apiRequest = request.newBuilder()
            .url(request.url.newBuilder().removeAllQueryParameters(COVER_PARAM).build())
            .header("Accept", "application/json")
            .build()
        val html = chain.proceed(apiRequest).parseAs<List<PostContentDto>>().firstOrNull()?.content?.rendered.orEmpty()
        val cover = Jsoup.parseBodyFragment(html, baseUrl).select("img")
            .map { it.absUrl("data-src").ifEmpty { it.absUrl("src") } }
            .firstOrNull { it.isNotEmpty() }
            ?: throw IOException("Sin portada")
        // Image hosts like imgur reject foreign Referers, so only keep the User-Agent
        val imageRequest = Request.Builder().url(cover)
            .apply { request.header("User-Agent")?.let { header("User-Agent", it) } }
            .build()
        return chain.proceed(imageRequest)
    }

    // =============================== Helpers ==============================

    private fun apiUrl(endpoint: String): HttpUrl.Builder = "$baseUrl/wp-json/wp/v2/$endpoint".toHttpUrl().newBuilder()

    private fun postsUrl(search: String?): HttpUrl.Builder = apiUrl("posts").apply {
        if (!search.isNullOrBlank()) addQueryParameter("search", search)
    }

    private suspend inline fun <reified T> fetchAll(url: HttpUrl.Builder, fields: String, maxPages: Int = Int.MAX_VALUE): List<T> {
        url.setQueryParameter("per_page", "100").setQueryParameter("_fields", fields)
        val first = client.get(url.setQueryParameter("page", "1").build(), Fetch.XHR)
        val totalPages = minOf(first.header("X-WP-TotalPages")?.toIntOrNull() ?: 1, maxPages)
        val firstItems = first.parseAs<List<T>>()
        if (totalPages <= 1) return firstItems
        val pageUrls = (2..totalPages).map { url.setQueryParameter("page", it.toString()).build() }
        val rest = coroutineScope {
            pageUrls.map { async { client.get(it, Fetch.XHR).parseAs<List<T>>() } }.awaitAll()
        }
        return firstItems + rest.flatten()
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val SEARCH_PAGES = 3
        private const val COVER_PARAM = "tvycover"
        private const val POST_FIELDS = "id,date_gmt,slug,title,categories"
    }
}
