package eu.kanade.tachiyomi.extension.all.fsicomics

import android.util.LruCache
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
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
import kotlin.time.Instant

@Source
abstract class FsiComics : KeiSource() {

    private val apiLimit = 20

    private val scaledSuffix = """-scaled(\.\w+)$""".toRegex()

    // The publisher paragraph follows the description; non-EN sites machine-translate it.
    private val descriptionEndMarkers = when (lang) {
        "es" -> listOf("es el editor de este episodio")
        "fr" -> listOf("est l’auteur de cet épisode", "publie cet épisode")
        "de" -> listOf("ist der Herausgeber dieser", "ist die Herausgeberin dieser")
        "it" -> listOf("è l’editore di questo episodio")
        else -> listOf("Publisher of This Comic Book Episode", "Watch comics feature genres")
    }

    // Only the EN site has a video category; other locales returned none.
    private val excludedCategories: Set<Int> = when (lang) {
        "en" -> setOf(320)
        else -> emptySet()
    }

    private val filterParentId = when (lang) {
        "en" -> 318
        else -> 0
    }

    private val tagIdCache = LruCache<String, String>(20)

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        // wordpress-popular-posts exposes a public REST route; last30days mirrors
        // the site's "Trending Comics This Month" section.
        val url = "$baseUrl/wp-json/wordpress-popular-posts/v1/popular-posts".toHttpUrl().newBuilder()
            .addQueryParameter("limit", apiLimit.toString())
            .addQueryParameter("offset", ((page - 1) * apiLimit).toString())
            .addQueryParameter("range", "last30days")
            .addQueryParameter("order_by", "views")
            .addQueryParameter("embed", "true")
            .addQueryParameter("_embed", "wp:featuredmedia")
            .addQueryParameter("_fields", "title,link,categories,_embedded,_links.wp:featuredmedia")
            .build()

        val result = client.get(url).parseAs<List<WPPostDto>>()
        val mangas = result
            .filter { it.categories.none(excludedCategories::contains) }
            .map { it.toSManga() }
        return MangasPage(mangas, result.size >= apiLimit)
    }

    // ============================== Latest ==============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", apiLimit.toString())
            .addQueryParameter("_embed", "wp:featuredmedia")
            .addQueryParameter("_fields", "link,title,categories,_embedded,_links.wp:featuredmedia")
            .build()
        return fetchPosts(url)
    }

    private suspend fun fetchPosts(url: HttpUrl): MangasPage {
        val response = client.get(url)
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 0
        val currentPage = url.queryParameter("page")?.toIntOrNull() ?: 0
        val mangas = response.parseAs<List<WPPostDto>>()
            .filter { it.categories.none(excludedCategories::contains) }
            .map { it.toSManga() }
        return MangasPage(mangas, currentPage < totalPages)
    }

    // ============================== Search ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 1) return null

        val document = client.get(url).asJsoup()
        return SManga.create().apply {
            this.url = url.encodedPath
            title = document.selectFirst("h1.s-title")?.text() ?: throw Exception("Title is mandatory")
            thumbnail_url = document.selectFirst("meta[property=\"og:image\"]")?.attr("content")
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }
        val tagQuery = filters.firstInstanceOrNull<TagFilter>()?.state?.takeIf { it.isNotBlank() }
        val tag = tagQuery?.let {
            val key = it.trim().lowercase()
            tagIdCache[key] ?: runCatching { resolveTagId(it) }.getOrNull()?.also { id -> tagIdCache.put(key, id) }
        }
        if (tagQuery != null && tag == null) return MangasPage(emptyList(), false)
        val sort = filters.firstInstanceOrNull<SortFilter>()?.state ?: 0

        val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", apiLimit.toString())
            .addQueryParameter("_embed", "wp:featuredmedia")
            .addQueryParameter("_fields", "link,title,categories,_embedded,_links.wp:featuredmedia")
            .apply {
                if (query.isNotEmpty()) addQueryParameter("search", query)
                category?.let { addQueryParameter("categories", it) }
                tag?.let { addQueryParameter("tags", it) }
                when (sort) {
                    1 -> {
                        addQueryParameter("orderby", "date")
                        addQueryParameter("order", "asc")
                    }

                    2 -> {
                        if (query.isNotEmpty()) {
                            addQueryParameter("orderby", "relevance")
                            addQueryParameter("order", "desc")
                        } else {
                            addQueryParameter("orderby", "date")
                            addQueryParameter("order", "desc")
                        }
                    }

                    3 -> {
                        addQueryParameter("orderby", "title")
                        addQueryParameter("order", "asc")
                    } else -> addQueryParameter("orderby", "date")
                }
            }
            .build()
        return fetchPosts(url)
    }

    private suspend fun resolveTagId(query: String): String? {
        val norm = query.trim().lowercase()
        val tags = client.get(
            "$baseUrl/wp-json/wp/v2/tags".toHttpUrl().newBuilder()
                .addQueryParameter("search", query.trim())
                .addQueryParameter("per_page", "100")
                .addQueryParameter("_fields", "id,name,slug")
                .build(),
        ).parseAs<List<WPTagDto>>()
        return tags.firstOrNull { it.name.equals(norm, ignoreCase = true) || it.slug.equals(norm, ignoreCase = true) }
            ?.id?.toString()
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val mangaTitle = document.selectFirst("h1.s-title")?.text() ?: throw Exception("Title is mandatory")

        val updatedManga = SManga.create().apply {
            url = manga.url
            title = mangaTitle
            description = document.select("div.entry-content > p.wp-block-paragraph")
                .takeWhile { p -> descriptionEndMarkers.none { p.text().contains(it, ignoreCase = true) } }
                .joinToString("\n\n") { it.text() }
                .ifBlank { null }
            genre = document.select("a[rel=tag]").joinToString { it.text() }
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }

        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            date_upload = Instant.tryParse(document.selectFirst("time.published")?.attr("datetime"))
        }

        return SMangaUpdate(updatedManga, listOf(chapter))
    }

    // ============================== Pages ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select(".entry-content .wp-block-gallery img")
        .map { it.attr("abs:src") }
        .filter { it.isNotEmpty() && !it.startsWith("data:image") }
        .map { scaledSuffix.replace(it, "$1") }
        .distinct()
        .mapIndexed { i, url -> Page(i, imageUrl = url) }

    // ============================== Filters ==============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val first = client.get("$baseUrl/wp-json/wp/v2/categories?per_page=100&page=1&_fields=id,parent,name,slug")
        val totalPages = first.header("X-WP-TotalPages")?.toIntOrNull() ?: 1
        val categories = first.parseAs<List<WPCategoryDto>>() +
            (2..totalPages).map { page ->
                async {
                    client.get("$baseUrl/wp-json/wp/v2/categories?per_page=100&page=$page&_fields=id,parent,name,slug")
                        .parseAs<List<WPCategoryDto>>()
                }
            }.awaitAll().flatten()

        FilterData(categories).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: FilterData()
        val children = filterData.categories.groupBy { it.parent }
        fun subtree(id: Int): List<Int> = listOf(id) + children[id].orEmpty().flatMap { subtree(it.id) }
        val categories = filterData.categories
            .filter { it.parent == filterParentId && it.slug != "uncategorized" && it.slug != "uncategorised" }
            .map { it.name to subtree(it.id).joinToString(",") }
        return FilterList(
            SortFilter(),
            CategoryFilter(listOf("Any" to "") + categories),
            TagFilter(),
        )
    }
}
