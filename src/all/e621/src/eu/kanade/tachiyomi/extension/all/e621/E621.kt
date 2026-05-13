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
    override fun popularMangaRequest(page: Int): Request {
        // val tagModeEnabled = preferences.tagModeEnablePref
        //
        // if (tagModeEnabled) {
        //
        // } else {
        //     if (category.isNotEmpty()) {
        //         val url = "$baseUrl/pools.json?limit=24&page=$page&search[order]=post_count".toHttpUrl().newBuilder()
        //         val category = preferences.categoryPref
        //         url.addQueryParameter("search[category]", category)
        //         Log.d("app.mihon:E621", "GET1 ${url.build()}")
        //         return GET(url.build(), headers)
        //     }
        // }

        // Log.d("app.mihon:E621", "Cache: ${network.client.cache}")
        // network.client.cache?.let { cache ->
        //     Log.d("app.mihon:E621", "Size: ${cache.size()}")
        //     Log.d("app.mihon:E621", "Max size: ${cache.maxSize()}")
        //     Log.d("app.mihon:E621", "Directory: ${cache.directory}")
        // }

        val searchMode = preferences.searchModePref
        val category = preferences.categoryPref

        // A little hacky, but it helps unify things
        return searchMangaRequest(
            page,
            "",
            FilterList(
                ModeFilter(getDefaultModeIndex(searchMode)),
                CategoryFilter(getDefaultCategoryIndex(category)),
                OrderFilter(getDefaultOrderIndex("post_count")),
                TagsFilter("order:score ( ~first_page ~end_page )"),
            ),
        )
        // return GET("", headers)
    }

    // override fun popularMangaParse(response: Response): MangasPage = parsePoolList(response)
    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        // val url = "$baseUrl/pools.json?limit=24&page=$page&search[order]=created_at".toHttpUrl().newBuilder()
        // val category = preferences.categoryPref
        // if (category.isNotEmpty()) {
        //     url.addQueryParameter("search[category]", category)
        // }
        // Log.d("app.mihon:E621", "GET2 ${url.build()}")
        // return GET(url.build(), headers)

        val searchMode = preferences.searchModePref
        val category = preferences.categoryPref

        // A little hacky, but it helps unify things
        return searchMangaRequest(
            page,
            "",
            FilterList(
                ModeFilter(getDefaultModeIndex(searchMode)),
                CategoryFilter(getDefaultCategoryIndex(category)),
                OrderFilter(getDefaultOrderIndex("created_at")),
                TagsFilter("order:id_desc score:>20"),
            ),
        )
    }

    // override fun latestUpdatesParse(response: Response): MangasPage = parsePoolList(response)
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

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
        var orderTag = ""
        var tags = ""
        var firstPage = false
        var endPage = false
        var dateTag = ""

        filters.forEach { filter ->
            when (filter) {
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
            // if (query.isNotEmpty()) {
            //     // Glob tag search
            //     val search = "*${query.replace(" ", "_")}*"
            //     url.addQueryParameter("tags", "$tagsMandatory $tags $search")
            // } else {
            //     url.addQueryParameter("tags", "$tagsMandatory $tags")
            // }

            tags = "$tagsMandatory $tags".trim()
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
            // Since two requests are made in this mode, and duplicates are culled, I've quadrupled
            // the limit to help reduce requests-per-second.
            url.addQueryParameter("limit", "96")
            url.addQueryParameter("tags", "$tags")
        }

        Log.d("app.mihon:E621", "GET3 $url")
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
        // Log.d("app.mihon:E621", "RAW:\n" + posts.toString())
        // var testlog = "TEST:\n" + posts.joinToString(separator = "\n") { it.poolIds.toString() }
        // Log.d("app.mihon:E621", testlog)
        // due to shared pools between posts, we cant assume there isn't a next page until empty
        var test = parsePoolListDirect(pools, posts.size >= 96)
        Log.d("app.mihon:E621", "SIZES: ${posts.size} ${poolIds.size} ${pools.size}")
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
        Log.d("app.mihon:E621", "GET4 $baseUrl/pools/$poolId.json")
        return GET("$baseUrl/pools/$poolId.json", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val pool = response.parseAs<Pool>()

        // fetch first 40 posts for common tags and artist
        val posts = batchFetchPosts(pool.postIds.take(40))
        // fetch last 40 posts for common tags and artist
        // val posts = batchFetchPosts(pool.postIds.takeLast(40))
        val artists = posts[0].tags.artist
        val rating = when {
            posts.any { it.rating == "e" } -> "rating:Explicit"
            posts.any { it.rating == "q" } -> "rating:Questionable"
            else -> "rating:Safe"
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
            // .sortedBy { it.first }                          // sort alphabetically
            .map { it.first }

        // Log.d("app.mihon:E621", tags.toString())

        return SManga.create().apply {
            url = pool.id.toString()
            title = pool.name.replace("_", " ")
            description = pool.description

            status = when (pool.isActive) {
                true -> SManga.ONGOING
                false -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            genre = "$rating, " + tags.joinToString(", ")
            author = artists.joinToString(", ")
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/pools/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val poolId = manga.url
        Log.d("app.mihon:E621", "GET5 $baseUrl/pools/$poolId.json")
        return GET("$baseUrl/pools/$poolId.json", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val pool = response.parseAs<Pool>()
        val postIds = pool.postIds
        // val updatedAt = pool.updatedAt
        val title = pool.name.replace("_", " ")

        return if (preferences.splitChaptersPref == "chapters") {
            // 1) get first post, get it's pools. Use the one that isn't self.
            // 2) If there are more that aren't self, then select the smallest one
            // 3) Get the size of the pool, and get the Nth post of the larger pool
            //    and repeat the process until end of pool is reached.
            // If posts are in the super pool, but not a minor pool, then they each get their own
            // chapter.

            // Fetch all posts in the pool
            val posts = batchFetchPosts(postIds)

            // // Pair pools with counts and starting post
            // val poolsSized = posts.flatMap { it.poolIds }.groupingBy { it }.eachCount()
            // val poolsFirst = posts.flatMap { it.poolIds } { post ->
            //     post.poolIds.map { it to post.id }
            // }

            // TODO: replace poolsGroups with pools

            val poolsGroups = posts.flatMap { post -> post.poolIds.map { it to post.id } }
                .groupBy({ (poolId, _) -> poolId }, { (_, postId) -> postId })
                .filterValues { it.size < pool.postIds.size }.toMutableMap()
            // poolsGroups.remove(pool.id)

            // Fetch pools for names and dates
            // val poolIds = posts.flatMap { it.poolIds }.toMutableSet().remove(pool.id).toList()
            val getPoolIds = posts.flatMap { it.poolIds }.toSet().toList()
            val subPools = batchFetchPools(getPoolIds).associate { it.id to it }
                .filterValues { it.postIds.size < pool.postIds.size }
            val poolIds = subPools.keys

            // Log.d("app.mihon:E621", "SUBPOOLS DETECTED: $poolIds $poolsGroups")

            // For every post (must iterate over every post as a guarentee)
            // If the first post in a pool, then add smallest pool as first chapter
            // If not the first post, but in the pool of the previous chapter, then skip
            // If not the first post, and not in the previous pool, then add as chapter

            //  For every post:
            //      // Get smallest subpool post is first in
            //      If one of post's pools are already a chapter, then ignore
            //      Else if first post in a non-added subpool, then add pool as chapter
            //      Else add post as chapter

            // We need to select the scheme that will result in more than one chapter,
            // So long as the number of chapters outweight the number of posts.
            // So we need to predict the number of chapters to posts ratio
            // OR we could just use what we already calculated, and if its count exceeds
            // the number of USED pools, then we opt for a single chapter

            // TODO: Plenty of room for optimization
            var n: Int = 0
            posts.mapNotNull { post ->
                val isInUsedPool: Boolean = post.poolIds.filter { it in subPools }
                    .any { it !in poolsGroups.keys }

                val minPoolFirstInId: Int = (!isInUsedPool).let {
                    poolsGroups.filter { it.value[0] == post.id }.minByOrNull { it.value.size }?.key
                } ?: 0

                // Log.d("app.mihon:E621", "POST:$n:${post.id} $minPoolFirstInId $isInUsedPool ${post.poolIds} ${subPools.keys} ${post.poolIds.filter { it in subPools }}")

                // skips processing every post if there is just the one pool
                // if (smallestUnusedPoolFirstIn == pool.id) {
                //     return listOf(
                //         SChapter.create().apply {
                //             name = "Pool (${postIds.size} pages)"
                //             url = "/pools/${pool.id}"
                //             chapter_number = 1f         // Stops missing chapters warning
                //             date_upload = parseDate(updatedAt)
                //         },
                //     )
                // }

                when {
                    isInUsedPool -> null

                    minPoolFirstInId > 0 -> {
                        val subPool = subPools[minPoolFirstInId] ?: pool
                        var chapterTitle = subPool.name.replace("_", " ")
                        if (chapterTitle == title) chapterTitle = "Chapter $n"

                        // Log.d(
                        //     "app.mihon:E621",
                        //     "POST:${subPool.id} TITLE: ${subPool.name} (${subPool.postIds.size} pages)",
                        // )

                        // pop pool group to indicate that it is now a chapter
                        poolsGroups.remove(minPoolFirstInId)

                        SChapter.create().apply {
                            // name = if (minPoolFirstInId == pool.id) {
                            //     "$title$title (${subPool.postIds.size} pages)"
                            // } else {
                            //     "$chapterTitle (${subPool.postIds.size} pages)"
                            // }
                            name = "$chapterTitle (${subPool.postIds.size} pages)"
                            url = "/pools/${subPool.id}"
                            chapter_number = (++n).toFloat()
                            // date_upload = if (n == 0) parseDate(updatedAt) else 0L
                            date_upload = parseDate(subPool.updatedAt)
                        }
                    }
                    else -> SChapter.create().apply {
                        name = "Post ${post.id}"
                        url = "/posts/${post.id}"
                        chapter_number = (++n).toFloat()
                        // date_upload = if (n == 0) parseDate(updatedAt) else 0L
                        date_upload = parseDate(post.createdAt)
                    }
                }
            }.reversed().takeIf {
                // Log.d("app.mihon:E621", "TEST: ${it.size} ${poolIds.size} ${poolsGroups.size}")
                it.size / 2 <= poolIds.size - poolsGroups.size
            } ?: listOf(
                SChapter.create().apply {
                    name = "$title$title (${pool.postIds.size} pages)"
                    url = "/pools/${pool.id}"
                    chapter_number = 1f
                    date_upload = parseDate(pool.updatedAt)
                },
            )

            // for n: Int = 0
            // while (n < postIds.size) {
            //
            // }

            // get all unique pools with their counts
            // cull the pools that aren't a subpool

            // The idea is to select the subpools that encompass all of the pools
            // Which can include the base pool.
            // If there is a post that starts out a pool but is not the first in that pool,
            // Then that pool

            // var chapters: mutableList<Any> = mutableListOf<Any>()
            //
            // var n: Int = 0
            // while (n < postIds.size) {
            //     val postId = postIds[n]
            //     val post = batchFetchPosts(listOf(postId))[0]
            //     val subPoolIds = post.poolIds.filter { it != pool.id }
            //     if (subPoolIds.size == 0) {
            //         // Case: No subpool - add post as chapter
            //         chapters.add(SChapter.create().apply {
            //             name = "Post $postId"
            //             url = "/posts/$postId"
            //             chapter_number = (index + 1).toFloat()
            //             date_upload = if (index == 0) parseDate(updatedAt) else 0L
            //         })
            //     } else {
            //         // need to pull pools either way to check it isn't secretly the larger pool
            //         val subpools = batchFetchPools(poolIds).filter { it.postIds.size < postIds.size }
            //         if (poolIds.size == 1) {
            //             // Case: One subpool
            //
            //         } else {
            //             // Case: Multiple subpools
            //         }
            //
            //     }
            // }
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
            // val title = pool.name.replace("_", " ")
            listOf(
                SChapter.create().apply {
                    name = "$title$title (${postIds.size} pages)"
                    // name = "Pool ${pool.id} (${postIds.size} pages)"
                    // name = "Pool (${postIds.size} pages)"
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
            Log.d("app.mihon:E621", "GET6 $url")
            GET(url, headers)
        } else {
            val poolId = chapterUrl.pathSegments.last()
            Log.d("app.mihon:E621", "GET7 $baseUrl/pools/$poolId.json")
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

        // increased chunk size from 40 to reduce requests for large pools
        return postIds.chunked(200).flatMap { chunk ->
            runCatching {
                // val tagQuery = chunk.joinToString(" ") { "~id:$it" }
                val tagQuery = "id:" + chunk.joinToString(",")
                val url = "$baseUrl/posts.json".toHttpUrl().newBuilder()
                    .addQueryParameter("tags", tagQuery)
                    .addQueryParameter("limit", chunk.size.toString())
                    .build()

                Log.d("app.mihon:E621", "GET9 $url")
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

                Log.d("app.mihon:E621", "GET10 $url")
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
