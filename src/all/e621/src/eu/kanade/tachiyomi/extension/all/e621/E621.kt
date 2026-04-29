package eu.kanade.tachiyomi.extension.all.e621

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class E621 :
    HttpSource(),
    ConfigurableSource {

    override val name: String = "e621"
    override val baseUrl: String = "https://e621.net"
    override val lang: String = "all"
    override val supportsLatest: Boolean = true

    override fun getFilterList(): FilterList = getE621FilterList(preferences.categoryPref)

    override fun setupPreferenceScreen(screen: PreferenceScreen) = setupE621PreferenceScreen(screen)

    override val client = network.cloudflareClient
    private val preferences: SharedPreferences by getPreferencesLazy()

    // e621 needs a custom User-Agent header
    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "E621/1.4.${BuildConfig.VERSION_CODE} Keiyoushi (https://github.com/keiyoushi/extensions-source)")

    private val artistFilter = setOf(
        "conditional_dnp",
        "unknown_artist",
        "third-party_edit",
        "sound_warning",
        "anonymous_artist",
    )

    // Popular
    override fun popularMangaRequest(page: Int): Request { // e621 doesn't have a "popular" page, so we'll just sort by post count
        val url = "$baseUrl/pools.json?limit=24&page=$page&search[order]=post_count".toHttpUrl().newBuilder()
        val category = preferences.categoryPref
        if (category.isNotEmpty()) {
            url.addQueryParameter("search[category]", category)
        }
        return GET(url.build(), headers)
    }
    override fun popularMangaParse(response: Response): MangasPage = parsePoolList(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/pools.json?limit=24&page=$page&search[order]=created_at".toHttpUrl().newBuilder()
        val category = preferences.categoryPref
        if (category.isNotEmpty()) {
            url.addQueryParameter("search[category]", category)
        }
        return GET(url.build(), headers)
    }
    override fun latestUpdatesParse(response: Response): MangasPage = parsePoolList(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/pools.json?limit=24&page=$page&search[order]=updated_at".toHttpUrl().newBuilder()
        var category = ""
        var order = "updated_at"
        var activeOnly = false
        var description = ""

        filters.forEach { filter ->
            when (filter) {
                is OrderFilter -> order = filter.toUriPart()
                is CategoryFilter -> category = filter.toUriPart()
                is ActiveOnlyFilter -> activeOnly = filter.state
                is DescriptionFilter -> description = filter.state.trim()
                else -> {}
            }
        }

        url.addQueryParameter("search[order]", order)
        if (category.isNotEmpty()) url.addQueryParameter("search[category]", category)
        if (activeOnly) url.addQueryParameter("search[is_active]", "true")
        if (query.isNotEmpty()) {
            val search = "*${query.replace(" ", "_")}*"
            url.addQueryParameter("search[name_matches]", search)
        }
        if (description.isNotEmpty()) {
            url.addQueryParameter("search[description_matches]", description)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parsePoolList(response)

    private fun parsePoolList(response: Response): MangasPage {
        val pools = response.parseAs<List<Pool>>()

        val thumbnailMap = batchFetchPostSamples(
            pools.mapNotNull { it.postIds.firstOrNull() },
        ).takeIf { it.isNotEmpty() } ?: emptyMap()

        val poolList = pools.map { pool ->
            SManga.create().apply {
                url = pool.id.toString()
                title = pool.name.replace("_", " ")
                thumbnail_url = pool.postIds.firstOrNull()
                    ?.let { thumbnailMap[it] }
            }
        }

        return MangasPage(poolList, poolList.size >= 24)
    }

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request {
        val poolId = manga.url
        return GET("$baseUrl/pools/$poolId.json", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val pool = response.parseAs<Pool>()

        return SManga.create().apply {
            url = pool.id.toString()
            title = pool.name.replace("_", " ")
            description = pool.description

            status = when (pool.isActive) {
                true -> SManga.ONGOING
                false -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            genre = pool.category
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/pools/${manga.url}"

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val poolId = manga.url
        return GET("$baseUrl/pools/$poolId.json", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val pool = response.parseAs<Pool>()
        val postIds = pool.postIds
        val updatedAt = pool.updatedAt

        return if (preferences.splitChaptersPref && postIds.isNotEmpty()) {
            postIds.mapIndexed { index, postId ->
                SChapter.create().apply {
                    name = "Post ${index + 1}"
                    url = "/posts/$postId"
                    chapter_number = (index + 1).toFloat()
                    date_upload = if (index == 0) parseDate(updatedAt) else 0L
                }
            }.reversed()
        } else {
            listOf(
                SChapter.create().apply {
                    name = "Pool (${postIds.size} pages)"
                    url = "/pools/${pool.id}"
                    date_upload = parseDate(updatedAt)
                },
            )
        }
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = "$baseUrl${chapter.url}".toHttpUrl()

        return if (chapterUrl.pathSegments.getOrNull(0) == "posts") {
            val postId = chapterUrl.pathSegments.last().toIntOrNull()
            val url = "$baseUrl/posts.json".toHttpUrl().newBuilder().apply {
                if (postId != null) {
                    addQueryParameter("tags", "id:$postId")
                    addQueryParameter("limit", "1")
                }
            }.build()
            GET(url, headers)
        } else {
            val poolId = chapterUrl.pathSegments.last()
            GET("$baseUrl/pools/$poolId.json", headers)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url

        // Single post chapter (split chapters mode)
        if (url.encodedPath == "/posts.json") {
            val post = response.parseAs<PostsResponse>().posts.firstOrNull()
            val imageUrl = when {
                post == null -> "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Deleted" // Not returned by API
                isPostDeleted(post) -> "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Deleted"
                else -> extractImageUrl(post) ?: "https://placehold.co/256x256/cccccc/f66151.jpg?text=No%20Image"
            }
            return listOf(Page(0, imageUrl = imageUrl))
        }

        // Pool chapter with all pages
        val postIds = response.parseAs<Pool>().postIds
        if (postIds.isEmpty()) return emptyList()

        val posts = batchFetchPosts(postIds)
        val postMap = posts.associateBy { it.id }

        return postIds.mapIndexed { index, postId ->
            val post = postMap[postId]
            val imageUrl = when {
                post == null -> "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Deleted" // Not returned by API
                isPostDeleted(post) -> "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Deleted"
                else -> extractImageUrl(post) ?: "https://placehold.co/256x256/cccccc/f66151.jpg?text=No%20Image"
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Helpers

    private fun isPostDeleted(post: Post): Boolean = post.flags.deleted

    private fun extractThumbnailUrl(post: Post): String? {
        // Preview (smallest, fastest to load)
        post.preview.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        // Sample
        post.sample.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        // Full Resolution
        post.file.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        return null
    }

    private fun extractImageUrl(post: Post): String? {
        // Full Resolution (best quality for reading)
        post.file.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        // Sample
        post.sample.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        // Preview
        post.preview.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        return null
    }

    private fun batchFetchPosts(postIds: List<Int>): List<Post> {
        if (postIds.isEmpty()) return emptyList()

        return postIds.chunked(40).flatMap { chunk ->
            runCatching {
                val tagQuery = chunk.joinToString(" ") { "~id:$it" }
                val url = "$baseUrl/posts.json".toHttpUrl().newBuilder()
                    .addQueryParameter("tags", tagQuery)
                    .addQueryParameter("limit", chunk.size.toString())
                    .build()

                val data = client.newCall(GET(url, headers)).execute()
                    .parseAs<PostsResponse>()

                data.posts
            }.getOrDefault(emptyList())
        }
    }

    private fun batchFetchPostSamples(postIds: List<Int>): Map<Int, String> {
        if (postIds.isEmpty()) return emptyMap()

        return batchFetchPosts(postIds).mapNotNull { post ->
            extractThumbnailUrl(post)?.let { post.id to it }
        }.toMap()
    }

    private fun parseDate(dateStr: String): Long = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).tryParse(dateStr)
}
