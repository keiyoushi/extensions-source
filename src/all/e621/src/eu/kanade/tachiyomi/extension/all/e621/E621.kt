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
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT)

private const val DELETED_PLACEHOLDER = "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Deleted"
private const val BLACKLISTED_PLACEHOLDER = "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Blacklisted"
private const val NO_IMAGE_PLACEHOLDER = "https://placehold.co/256x256/cccccc/f66151.jpg?text=No%20Image"

@Source
abstract class E621 :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest: Boolean = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    @Volatile private var cachedAccountBlacklist: String? = null

    @Volatile private var cachedAccountBlacklistCredentials: String? = null

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "E621/1.4.${BuildConfig.VERSION_CODE} Keiyoushi (https://github.com/keiyoushi/extensions-source)")

    private fun apiHeaders(): Headers = headersBuilder().apply {
        val username = preferences.usernamePref.trim()
        val apiKey = preferences.apiKeyPref.trim()
        if (username.isNotEmpty() && apiKey.isNotEmpty()) {
            add("Authorization", Credentials.basic(username, apiKey))
        }
    }.build()

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val searchMode = preferences.searchModePref
        val category = preferences.categoryPref
        val popularMode = preferences.popularModePref
        val firstEnd = preferences.firstEndPref

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

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val searchMode = preferences.searchModePref
        val category = preferences.categoryPref
        val scoreThresh = preferences.scoreThreshPref

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

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("page", "$page")

        var mode = "pools.json"
        var category = ""
        var order = "updated_at"
        var activeOnly = false
        var description = ""

        val tagsMandatory = "inpool:true -video status:any"
        var orderTag = ""
        var tags = ""
        var firstPage = false
        var endPage = false
        var dateTag = ""

        val blacklist = preferences.blacklistPref
        val whitelist = preferences.whitelistPref

        filters.forEach { filter ->
            when (filter) {
                is ModeFilter -> mode = filter.toUriPart()
                is CategoryFilter -> category = filter.toUriPart()
                is OrderFilter -> order = filter.toUriPart()
                is TagsFilter -> tags = filter.state.trim()
                is PoolGroupFilter -> {
                    category = filter.getCategory()
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
            tags = "$tagsMandatory $whitelist $blacklist $tags".trim()
            if (query.isNotEmpty()) {
                val search = "*${query.trim().replace(" ", "_")}*"
                tags = "$tags $search"
            }
            if (orderTag.isNotEmpty()) tags = "order:$orderTag $tags"
            if (dateTag.isNotEmpty()) tags = "date:$dateTag $tags"

            tags = when {
                firstPage && endPage -> "$tags ( ~first_page ~end_page )"
                firstPage -> "$tags first_page"
                endPage -> "$tags first_page"
                else -> tags
            }

            url.addQueryParameter("limit", "96")
            url.addQueryParameter("tags", tags)
        }

        return GET(url.build(), apiHeaders())
    }

    override fun searchMangaParse(response: Response): MangasPage = when (response.request.url.encodedPath) {
        "/pools.json" -> parsePoolList(response)
        "/posts.json" -> parsePostsList(response)
        else -> MangasPage(emptyList(), false)
    }

    private fun parsePoolList(response: Response): MangasPage {
        val pools = response.parseAs<List<Pool>>()
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
                thumbnail_url = pool.postIds.firstOrNull()?.let { thumbnailMap[it] }
            }
        }

        return MangasPage(poolList, hasNextPage)
    }

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/pools/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val poolId = manga.url
        return GET("$baseUrl/pools/$poolId.json", apiHeaders())
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val pool = response.parseAs<Pool>()

        val cutoff = (pool.postIds.size * 0.2).toInt()
        val posts = if (preferences.betterDetailsPref) {
            batchFetchPosts(pool.postIds.drop(cutoff).take(40))
        } else {
            emptyList()
        }

        val artists = posts.flatMap { it.tags.artist }.toSet()
        val rating = when {
            posts.any { it.rating == "e" } -> "rating:Explicit, "
            posts.any { it.rating == "q" } -> "rating:Questionable, "
            posts.any { it.rating == "s" } -> "rating:Safe, "
            else -> ""
        }

        val medScore = posts.map { it.score.total }.sorted().getOrNull(posts.size / 2) ?: -99999
        val score = if (medScore != -99999) "score:>${medScore - 1}, " else ""

        val tags = posts.flatMap { post ->
            listOf(
                post.tags.lore.map { "lore" to it },
                post.tags.general.map { "general" to it },
                post.tags.species.map { "species" to it },
                post.tags.character.map { "character" to it },
                post.tags.copyright.map { "copyright" to it },
            ).flatten()
        }.groupBy({ it.first }, { it.second })
            .map { (genre, tags) ->
                genre to tags.groupingBy { it }
                    .eachCount()
                    .filter { it.key !in tagFilter }
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key }
            }.flatMap { (genre, tags) ->
                when (genre) {
                    "general" -> tags.take(15)
                    "copyright" -> tags.take(3)
                    "character" -> tags.take(5)
                    "species" -> tags.take(5)
                    "lore" -> tags.take(5)
                    else -> emptyList()
                }
            }

        return SManga.create().apply {
            url = pool.id.toString()
            title = pool.name.replace("_", " ")
            description = if (pool.description.length > 400) {
                pool.description.take(400) + " ..."
            } else {
                pool.description
            }
            status = when (pool.isActive) {
                true -> SManga.ONGOING
                false -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = "$rating$score" + tags.joinToString()
            author = artists.filter { it !in tagFilter }.joinToString()
        }
    }

    // ============================= Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val poolId = manga.url
        return GET("$baseUrl/pools/$poolId.json", apiHeaders())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val pool = response.parseAs<Pool>()
        val postIds = pool.postIds
        val title = pool.name.replace("_", " ")

        if (postIds.isEmpty()) return emptyList()

        val betterDetailsPref = preferences.betterDetailsPref
        val splitChaptersPref = preferences.splitChaptersPref
            .takeIf { preferences.splitChaptersPref != "chapters" || betterDetailsPref } ?: "merged"

        return when (splitChaptersPref) {
            "chapters" -> {
                val posts = batchFetchPosts(postIds)
                val poolIds = posts.flatMap { it.poolIds }.toSet().toList()
                val subPools = batchFetchPools(poolIds)
                    .associateBy { it.id }
                    .filterValues { it.postIds.size <= pool.postIds.size && it.id != pool.id }

                val usedPools = mutableMapOf<Int, Pool>()
                var n = 0

                posts.mapNotNull { post ->
                    val isInUsedPool = post.poolIds.any { it in usedPools }
                    val minPoolFirstInId = (!isInUsedPool).let {
                        subPools.filter { it.key !in usedPools && post.id in it.value.postIds.take(5) }
                            .minByOrNull { it.value.postIds.size }?.key
                    } ?: 0

                    when {
                        isInUsedPool -> null
                        minPoolFirstInId > 0 -> {
                            val subPool = subPools[minPoolFirstInId]!!
                            var chapterTitle = subPool.name.replace("_", " ")
                            if (chapterTitle == title) chapterTitle = "Chapter $n"

                            usedPools[subPool.id] = subPool

                            SChapter.create().apply {
                                name = "$chapterTitle (${subPool.postIds.size} pages)"
                                url = "/pools/${subPool.id}"
                                chapter_number = (++n).toFloat()
                                date_upload = DATE_FORMAT.tryParse(subPool.updatedAt)
                            }
                        }
                        else -> SChapter.create().apply {
                            name = "Post #${post.id}"
                            url = "/posts/${post.id}"
                            chapter_number = (++n).toFloat()
                            date_upload = DATE_FORMAT.tryParse(post.createdAt)
                        }
                    }
                }.reversed().takeIf { it.size / 2 <= usedPools.size } ?: listOf(
                    SChapter.create().apply {
                        name = "\u200B$title (${pool.postIds.size} pages)"
                        url = "/pools/${pool.id}"
                        chapter_number = 1f
                        date_upload = DATE_FORMAT.tryParse(pool.updatedAt)
                    },
                )
            }
            "posts" -> {
                postIds.mapIndexed { index, postId ->
                    SChapter.create().apply {
                        name = "Post #$postId"
                        url = "/posts/$postId"
                        chapter_number = (index + 1).toFloat()
                        date_upload = DATE_FORMAT.tryParse(pool.updatedAt)
                    }
                }.reversed()
            }
            "merged" -> {
                listOf(
                    SChapter.create().apply {
                        name = "Pool #${pool.id} (${postIds.size} pages)"
                        url = "/pools/${pool.id}"
                        chapter_number = 1f
                        date_upload = DATE_FORMAT.tryParse(pool.updatedAt)
                    },
                )
            }
            else -> emptyList()
        }
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = "$baseUrl${chapter.url}".toHttpUrl()

        return when (chapterUrl.pathSegments.getOrNull(0)) {
            "posts" -> {
                val postId = chapterUrl.pathSegments.last().toIntOrNull()
                val url = "$baseUrl/posts.json".toHttpUrl().newBuilder().apply {
                    if (postId != null) {
                        addQueryParameter("tags", "id:$postId")
                        addQueryParameter("limit", "1")
                    }
                }.build()
                GET(url, apiHeaders())
            }
            "pools" -> {
                val poolId = chapterUrl.pathSegments.last()
                GET("$baseUrl/pools/$poolId.json", apiHeaders())
            }
            else -> GET("", apiHeaders())
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url

        val blacklist: List<List<String>> = if (preferences.accountBlacklistPref) {
            fetchAccountBlacklist()
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line -> line.split(Regex("\\s+")).filter { it.isNotEmpty() } }
        } else {
            emptyList()
        }

        if (url.encodedPath == "/posts.json") {
            val post = response.parseAs<PostsResponse>().posts.firstOrNull()
            val imageUrl = when {
                post == null || isPostDeleted(post) -> DELETED_PLACEHOLDER
                isBlacklisted(post, blacklist) -> BLACKLISTED_PLACEHOLDER
                else -> extractImageUrl(post) ?: NO_IMAGE_PLACEHOLDER
            }
            return listOf(Page(0, imageUrl = imageUrl))
        }

        val postIds = response.parseAs<Pool>().postIds
        if (postIds.isEmpty()) return emptyList()

        val posts = batchFetchPosts(postIds)
        val postMap = posts.associateBy { it.id }

        return postIds.mapIndexed { index, postId ->
            val post = postMap[postId]
            val imageUrl = when {
                post == null || isPostDeleted(post) -> DELETED_PLACEHOLDER
                isBlacklisted(post, blacklist) -> BLACKLISTED_PLACEHOLDER
                else -> extractImageUrl(post) ?: NO_IMAGE_PLACEHOLDER
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = getE621FilterList(preferences.categoryPref)

    // ============================= Utilities =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) = setupE621PreferenceScreen(screen)

    private fun isPostDeleted(post: Post): Boolean = post.flags.deleted

    private fun isBlacklisted(post: Post, blacklist: List<List<String>>): Boolean {
        if (blacklist.isEmpty()) return false
        val allTags = post.tags.allTags
        return blacklist.any { group -> group.all { tag -> tag in allTags } }
    }

    private fun extractThumbnailUrl(post: Post): String? {
        post.preview.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }
        post.sample.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }
        post.file.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }
        return null
    }

    private fun extractImageUrl(post: Post): String? {
        post.file.url
            ?.takeIf {
                preferences.fullResolution ||
                    !post.sample.has ||
                    post.sample.width < 800 ||
                    post.sample.height < 1200
            }
            ?.let {
                if (it != "null" && it.isNotEmpty()) return it
            }

        post.sample.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        post.preview.url?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }
        return null
    }

    private fun batchFetchPosts(postIds: List<Int>): List<Post> {
        if (postIds.isEmpty()) return emptyList()

        return postIds.chunked(200).flatMap { chunk ->
            runCatching {
                val tagQuery = "status:all id:" + chunk.joinToString(",")
                val url = "$baseUrl/posts.json".toHttpUrl().newBuilder()
                    .addQueryParameter("tags", tagQuery)
                    .addQueryParameter("limit", chunk.size.toString())
                    .build()

                val data = client.newCall(GET(url, apiHeaders())).execute()
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

                val data = client.newCall(GET(url, apiHeaders())).execute()
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

    private fun fetchAccountBlacklist(): String {
        if (!preferences.accountBlacklistPref) return ""

        val username = preferences.usernamePref.trim()
        val apiKey = preferences.apiKeyPref.trim()
        if (username.isEmpty() || apiKey.isEmpty()) return ""

        val credentials = "$username:$apiKey"
        val cached = cachedAccountBlacklist
        if (cached != null && cachedAccountBlacklistCredentials == credentials) {
            return cached
        }

        val blacklist = runCatching {
            client.newCall(GET("$baseUrl/users/me.json", apiHeaders())).execute().use { response ->
                if (!response.isSuccessful) return@use ""
                response.parseAs<UserMeResponse>().blacklistedTags ?: ""
            }
        }.getOrDefault("")

        cachedAccountBlacklist = blacklist
        cachedAccountBlacklistCredentials = credentials
        return blacklist
    }
}
