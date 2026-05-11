package eu.kanade.tachiyomi.extension.all.e621

import android.content.SharedPreferences
import android.util.Log // TODO: Delete this
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
        // var poolurl = "$baseUrl/pools.json?limit=24&page=$page&search[order]=updated_at".toHttpUrl().newBuilder()
        // var postsurl = "$baseUrl/posts.json?limit=24&page=$page&tags=$tags"

        var url = baseUrl.toHttpUrl().newBuilder()
            // .addPathSegment("pools.json")
            // .addQueryParameter("limit", "24")
            .addQueryParameter("page", "$page")

        var mode = "pools.json"
        var category = ""
        var order = "updated_at"
        var activeOnly = false
        var description = ""
        var tagsMandatory = "inpool:true -video"
        var tags = "$tagsMandatory"

        filters.forEach { filter ->
            when (filter) {
                is ModeFilter -> mode = filter.toUriPart()
                is OrderFilter -> order = filter.toUriPart()
                is CategoryFilter -> category = filter.toUriPart()
                is ActiveOnlyFilter -> activeOnly = filter.state
                is DescriptionFilter -> description = filter.state.trim()
                is TagsFilter -> tags = filter.state.trim()
                else -> {}
            }
        }

        url.addPathSegment(mode)

        if (mode == "pools.json") {
            // Pools
            url.addQueryParameter("search[order]", order).addQueryParameter("limit", "24")
            if (category.isNotEmpty()) url.addQueryParameter("search[category]", category)
            if (activeOnly) url.addQueryParameter("search[is_active]", "true")
            if (query.isNotEmpty()) {
                val search = "*${query.replace(" ", "_")}*"
                url.addQueryParameter("search[name_matches]", search)
            }
            if (description.isNotEmpty()) {
                url.addQueryParameter("search[description_matches]", description)
            }
        } else {
            // Posts
            // Since two requests are made in this mode, and duplicates are culled, I've quadrupled
            // the limit to help reduce requests-per-second.
            url.addQueryParameter("limit", "96")
            if (query.isNotEmpty()) {
                // Glob tag search
                val search = "*${query.replace(" ", "_")}*"
                url.addQueryParameter("tags", "$tagsMandatory $tags $search")
            } else {
                url.addQueryParameter("tags", "$tagsMandatory $tags")
            }
        }

        // TODO: Delete me
        Log.d("MIHON:E621", url.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        var mode = response.request.url.encodedPath

        if (mode == "/pools.json") {
            return parsePoolList(response)
        } else if (mode == "/posts.json") {
            return parsePostsList(response)
        }
        return MangasPage(emptyList(), false)
    }

    private fun parsePoolList(response: Response): MangasPage {
        val pools = response.parseAs<List<Pool>>()
        return parsePoolListDirect(pools, pools.size >= 24)
    }

    private fun parsePostsList(response: Response): MangasPage {
        val posts = response.parseAs<PostsResponse>().posts
        val poolIds = posts.flatMap { it.poolIds }
        val pools = batchFetchPools(poolIds)
        // Log.d("MIHON:E621", "RAW:\n" + posts.toString())
        // var testlog = "TEST:\n" + posts.joinToString(separator = "\n") { it.poolIds.toString() }
        // Log.d("MIHON:E621", testlog)
        // due to shared pools between posts, we cant assume there isn't a next page until empty
        var test = parsePoolListDirect(pools, posts.size >= 96)
        Log.d("MIHON:E621", "SIZES: ${posts.size} ${poolIds.size} ${pools.size}")
        return test
        // return parsePoolListDirect(pools)
        // return MangasPage(emptyList(), false)
    }

    // private fun parsePoolListDirect(pools: List<Pool>, hasNextPageThreshold: Int = 24): MangasPage {
    private fun parsePoolListDirect(pools: List<Pool>, hasNextPage: Boolean): MangasPage {
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

        return MangasPage(poolList, hasNextPage)
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

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

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
            val title = pool.name.replace("_", " ")
            listOf(
                SChapter.create().apply {
                    // name = "$title (${postIds.size} pages)"
                    name = "$title$title" // WHY??? Zero clue.
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

    private fun batchFetchPools(poolIds: List<Int>): List<Pool> {
        if (poolIds.isEmpty()) return emptyList()

        return poolIds.distinct().chunked(100).flatMap { chunk ->
            runCatching {
                val url = "$baseUrl/pools.json".toHttpUrl().newBuilder()
                    .addQueryParameter("search[order]", "id_desc")
                    .addQueryParameter("search[id]", chunk.joinToString(","))
                    .addQueryParameter("limit", chunk.size.toString())
                    .build()

                val data = client.newCall(GET(url, headers)).execute()
                    .parseAs<List<Pool>>()

                // Maybe not the most efficient way to sort this?
                data.sortedBy { chunk.indexOf(it.id) }
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
