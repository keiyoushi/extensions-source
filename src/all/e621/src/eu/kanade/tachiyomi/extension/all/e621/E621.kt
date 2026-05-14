package eu.kanade.tachiyomi.extension.all.e621

import android.content.SharedPreferences
import android.util.Log // For Debugging
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

    private val logTag = "app.mihon:E621" // For Debugging

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
    override fun popularMangaRequest(page: Int): Request {
        val searchMode = preferences.searchModePref
        val category = preferences.categoryPref
        val popularMode = preferences.popularModePref
        val firstEnd = preferences.firstEndPref

        // A little hacky, but it helps unify things
        return searchMangaRequest(
            page,
            "",
            FilterList(
                ModeFilter(getDefaultModeIndex(searchMode)),
                CategoryFilter(getDefaultCategoryIndex(category)),
                OrderFilter(getDefaultOrderIndex("post_count")),
                TagsFilter("$popularMode $firstEnd".trim()),
            ),
        )
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val searchMode = preferences.searchModePref
        val category = preferences.categoryPref
        var scoreThresh = preferences.scoreThreshPref

        // A little hacky, but it helps unify things
        return searchMangaRequest(
            page,
            "",
            FilterList(
                ModeFilter(getDefaultModeIndex(searchMode)),
                CategoryFilter(getDefaultCategoryIndex(category)),
                OrderFilter(getDefaultOrderIndex("created_at")),
                TagsFilter("order:id_desc score:>=$scoreThresh"),
            ),
        )
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("page", "$page")

        var mode = "pools.json"
        var category = ""
        var order = "updated_at"
        var activeOnly = false
        var description = ""

        var tagsMandatory = "inpool:true -video status:any"
        var orderTag = ""
        var tags = ""
        var firstPage = false
        var endPage = false
        var dateTag = ""

        var blacklist = preferences.blacklistPref

        filters.forEach { filter ->
            when (filter) {
                // Keep these for popularMangaRequest and etc. compatibility
                is ModeFilter -> mode = filter.toUriPart()
                is CategoryFilter -> category = filter.toUriPart()
                is OrderFilter -> order = filter.toUriPart()
                is TagsFilter -> tags = filter.state.trim()
                // is ActiveOnlyFilter -> activeOnly = filter.state
                // is DescriptionFilter -> description = filter.state.trim()
                is PoolGroupFilter -> {
                    category = filter.getDescription()
                    order = filter.getOrder()
                    activeOnly = filter.getActiveOnly()
                    description = filter.getDescription()
                }
                is TagGroupFilter -> {
                    orderTag = filter.getOrderTag()
                    tags = filter.getTags()
                    firstPage = filter.getFirstPage()
                    endPage = filter.getEndPage()
                    dateTag = filter.getDate()
                }
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
                val search = "*${query.trim().replace(" ", "_")}*"
                url.addQueryParameter("search[name_matches]", search)
            }
            if (description.isNotEmpty()) {
                url.addQueryParameter("search[description_matches]", description)
            }
        } else {
            // Posts
            tags = "$tagsMandatory $tags $blacklist".trim()
            if (query.isNotEmpty()) {
                val search = "*${query.trim().replace(" ", "_")}*"
                tags = "$tags $search"
            }
            if (orderTag.isNotEmpty()) tags = "$tags order:$orderTag"
            if (dateTag.isNotEmpty()) tags = "$tags date:$dateTag"
            if (firstPage && endPage) {
                tags = "$tags ( ~first_page ~end_page )"
            } else if (firstPage) {
                tags = "$tags first_page"
            } else if (endPage) {
                tags = "$tags first_page"
            }
            // I've quadrupled the limit due to increased requests and duplicate
            // culling while in this mode. Helps to reduce API requests
            url.addQueryParameter("limit", "96")
            url.addQueryParameter("tags", "$tags")
        }

        Log.d(logTag, "GET $url") // DEBUG
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
        // BUG: false alarm 'No results found' when the total number of posts is a multiple of 24
        return parsePoolListDirect(pools, pools.size >= 24)
    }

    private fun parsePostsList(response: Response): MangasPage {
        val posts = response.parseAs<PostsResponse>().posts
        val poolIds = posts.flatMap { it.poolIds }
        val pools = batchFetchPools(poolIds)
        return parsePoolListDirect(pools, posts.size >= 96)
    }

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
        val url = "$baseUrl/pools/$poolId.json"
        Log.d(logTag, "GET $url") // DEBUG
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val pool = response.parseAs<Pool>()

        // fetch first 40 posts for common tags and artist
        // cut off first 20% to try to get more relevant tags
        val cutoff = (pool.postIds.size * 0.2).toInt()
        val posts = batchFetchPosts(pool.postIds.drop(cutoff).take(40))

        val artists = posts.flatMap { it.tags.artist }.toSet()
        val rating = when {
            posts.any { it.rating == "e" } -> "Explicit"
            posts.any { it.rating == "q" } -> "Questionable"
            else -> "Safe"
        }
        val tags = posts.flatMap {
            it.tags.general +
                // it.tags.artist +
                it.tags.copyright +
                it.tags.character +
                it.tags.species +
                it.tags.lore
        }.groupingBy { it }.eachCount()
            .filter { it.value >= posts.size / 2 }.toList() // >50% of posts have tag
            .sortedByDescending { it.second } // sort by count
            // .sortedBy { it.first } // sort alphabetically
            .map { it.first }

        return SManga.create().apply {
            url = pool.id.toString()
            title = pool.name.replace("_", " ")
            description = pool.description

            status = when (pool.isActive) {
                true -> SManga.ONGOING
                false -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            genre = "rating:$rating, " + tags.joinToString(", ")
            author = artists.joinToString(", ")
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/pools/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val poolId = manga.url
        val url = "$baseUrl/pools/$poolId.json"
        Log.d(logTag, "GET $url") // DEBUG
        return GET(url, headers)
    }

    // Big chonker function
    override fun chapterListParse(response: Response): List<SChapter> {
        val pool = response.parseAs<Pool>()
        val postIds = pool.postIds
        val title = pool.name.replace("_", " ")

        return if (preferences.splitChaptersPref == "chapters") {
            // fetch all posts for chapter detection
            val posts = batchFetchPosts(postIds)

            val poolIds = posts.flatMap { it.poolIds }.toSet().toList()
            val subPools = batchFetchPools(poolIds).associate { it.id to it }
                .filterValues { it.postIds.size < pool.postIds.size }

            var usedPools = mutableMapOf<Int, Pool>()

            // TODO: Plenty of room for optimization
            var n: Int = 0
            posts.mapNotNull { post ->
                val isInUsedPool: Boolean = post.poolIds.any { it in usedPools }

                val minPoolFirstInId: Int = (!isInUsedPool).let {
                    subPools.filter { it.key !in usedPools && it.value.postIds[0] == post.id }
                        .minByOrNull { it.value.postIds.size }?.key
                } ?: 0

                when {
                    // Skip Post
                    isInUsedPool -> null

                    // Add Pool
                    minPoolFirstInId > 0 -> {
                        val subPool = subPools[minPoolFirstInId]!!
                        var chapterTitle = subPool.name.replace("_", " ")
                        if (chapterTitle == title) chapterTitle = "Chapter $n"

                        usedPools.put(subPool.id, subPool)

                        SChapter.create().apply {
                            name = "$chapterTitle (${subPool.postIds.size} pages)"
                            url = "/pools/${subPool.id}"
                            chapter_number = (++n).toFloat()
                            date_upload = parseDate(subPool.updatedAt)
                        }
                    }

                    // Add Post
                    else -> SChapter.create().apply {
                        name = "Post ${post.id}"
                        url = "/posts/${post.id}"
                        chapter_number = (++n).toFloat()
                        date_upload = parseDate(post.createdAt)
                    }
                }
                // If more than half of the chapters are not pools, then merge into single pool
            }.reversed().takeIf { it.size / 2 <= usedPools.size } ?: listOf(
                SChapter.create().apply {
                    name = "$title$title (${pool.postIds.size} pages)"
                    url = "/pools/${pool.id}"
                    chapter_number = 1f
                    date_upload = parseDate(pool.updatedAt)
                },
            )
        } else if ((preferences.splitChaptersPref == "posts") && postIds.isNotEmpty()) {
            postIds.mapIndexed { index, postId ->
                SChapter.create().apply {
                    name = "Post ${index + 1}"
                    url = "/posts/$postId"
                    chapter_number = (index + 1).toFloat()
                    date_upload = if (index == 0) parseDate(pool.updatedAt) else 0L
                }
            }.reversed()
        } else if (preferences.splitChaptersPref == "merged") {
            listOf(
                SChapter.create().apply {
                    name = "$title$title (${postIds.size} pages)"
                    url = "/pools/${pool.id}"
                    chapter_number = 1f // Stops missing chapters warning
                    date_upload = parseDate(pool.updatedAt)
                },
            )
        } else {
            emptyList<SChapter>()
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
            Log.d(logTag, "GET $url") // DEBUG
            GET(url, headers)
        } else if (chapterUrl.pathSegments.getOrNull(0) == "pools") {
            val poolId = chapterUrl.pathSegments.last()
            val url = "$baseUrl/pools/$poolId.json"
            Log.d(logTag, "GET $url") // DEBUG
            GET(url, headers)
        } else if (chapterUrl.pathSegments.getOrNull(0) == "posts.json") {
            // For later
            GET("", headers)
        } else {
            GET("", headers)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url

        // Single post chapter (split chapters mode)
        if (url.encodedPath == "/posts.json") {
            // TODO: Change 'post' to 'posts'
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

        // increased chunk size from 40 to reduce requests for large pools
        return postIds.chunked(200).flatMap { chunk ->
            runCatching {
                val tagQuery = "status:all id:" + chunk.joinToString(",")
                val url = "$baseUrl/posts.json".toHttpUrl().newBuilder()
                    .addQueryParameter("tags", tagQuery)
                    .addQueryParameter("limit", chunk.size.toString())
                    .build()

                Log.d(logTag, "GET $url") // DEBUG
                val data = client.newCall(GET(url, headers)).execute()
                    .parseAs<PostsResponse>()

                data.posts.sortedBy { chunk.indexOf(it.id) }
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

                Log.d(logTag, "GET $url") // DEBUG
                val data = client.newCall(GET(url, headers)).execute()
                    .parseAs<List<Pool>>()

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
