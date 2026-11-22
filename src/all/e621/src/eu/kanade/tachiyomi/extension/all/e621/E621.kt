package eu.kanade.tachiyomi.extension.all.e621

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

private const val DELETED_PLACEHOLDER = "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Deleted"
private const val BLOCKED_PLACEHOLDER = "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Blocked"
private const val NO_IMAGE_PLACEHOLDER = "https://placehold.co/256x256/cccccc/f66151.jpg?text=No%20Image"

private val VIDEO_EXTENSIONS = setOf("webm", "mp4")

private val WHITESPACE_REGEX = Regex("""\s+""")

@Source
abstract class E621 :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    @Volatile private var cachedAccountBlockedTags: String? = null

    @Volatile private var cachedAccountBlockedTagsCredentials: String? = null

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("User-Agent", "E621/1.6.${BuildConfig.VERSION_CODE} Keiyoushi (https://github.com/keiyoushi/extensions-source)")

    private fun apiHeaders(): Headers = headersBuilder().apply {
        val username = preferences.usernamePref.trim()
        val apiKey = preferences.apiKeyPref.trim()
        if (username.isNotEmpty() && apiKey.isNotEmpty()) {
            set("Authorization", Credentials.basic(username, apiKey))
        }
    }.build()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val searchMode = preferences.searchModePref
        val category = preferences.categoryPref
        val popularMode = preferences.popularModePref

        return getSearchMangaList(
            page,
            "",
            FilterList(
                ModeFilter(getDefaultModeIndex(searchMode)),
                CategoryFilter(getDefaultCategoryIndex(category)),
                OrderFilter(getDefaultOrderIndex("post_count")),
                TagsFilter(popularMode),
            ),
        )
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val searchMode = preferences.searchModePref
        val category = preferences.categoryPref
        val scoreThresh = preferences.scoreThreshPref

        return getSearchMangaList(
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

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        val blockedTags = preferences.blockedTagsPref
        val requiredTags = preferences.requiredTagsPref

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
            tags = "$tagsMandatory $requiredTags $blockedTags $tags".trim()
            if (query.isNotEmpty()) {
                val search = "*${query.trim().replace(" ", "_")}*"
                tags = "$tags $search"
            }
            if (orderTag.isNotEmpty()) tags = "order:$orderTag $tags"
            if (dateTag.isNotEmpty()) tags = "date:$dateTag $tags"

            tags = when {
                firstPage && endPage -> "$tags ( ~first_page ~end_page )"
                firstPage -> "$tags first_page"
                endPage -> "$tags end_page"
                else -> tags
            }

            url.addQueryParameter("limit", "96")
            url.addQueryParameter("tags", tags)
            url.addQueryParameter("mode", "extended")
        }

        val response = client.get(url.build(), apiHeaders(), Fetch.XHR)

        return if (mode == "pools.json") {
            val pools = response.parseAs<List<Pool>>()
            parsePoolListDirect(pools, pools.size >= 24)
        } else {
            val posts = response.parseAs<List<Post>>()
            val poolIds = posts.flatMap { it.poolIds }
            val pools = batchFetchPools(poolIds)
            parsePoolListDirect(pools, posts.size >= 96)
        }
    }

    private suspend fun parsePoolListDirect(pools: List<Pool>, hasNextPage: Boolean): MangasPage {
        val thumbnailMap = batchFetchPostSamples(
            pools.mapNotNull { it.postIds.firstOrNull() },
        )

        val poolList = pools.map { pool ->
            SManga.create().apply {
                url = pool.id.toString()
                title = pool.name.replace("_", " ")
                thumbnail_url = pool.postIds.firstOrNull()?.let { thumbnailMap[it] }
            }
        }

        return MangasPage(poolList, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/pools/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host != baseUrl.toHttpUrl().host || segments.size != 2 || segments[0] != "pools") return null
        val poolId = segments[1].toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = poolId.toString() }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val pool = client.get("$baseUrl/pools/${manga.url}.json", apiHeaders(), Fetch.XHR).parseAs<Pool>()

        return coroutineScope {
            val details = async { if (fetchDetails) mangaDetailsParse(manga, pool) else manga }
            val chapterList = async { if (fetchChapters) chapterListParse(pool) else chapters }
            SMangaUpdate(details.await(), chapterList.await())
        }
    }

    private suspend fun mangaDetailsParse(manga: SManga, pool: Pool): SManga {
        val cutoff = (pool.postIds.size * 0.2).toInt()
        val posts = if (preferences.betterDetailsPref) {
            batchFetchPosts(pool.postIds.drop(cutoff).take(40))
        } else {
            emptyList()
        }

        val artists = posts.flatMap { it.tags["artist"].orEmpty() }.toSet()
        val rating = when {
            posts.any { it.rating == "e" } -> "rating:Explicit, "
            posts.any { it.rating == "q" } -> "rating:Questionable, "
            posts.any { it.rating == "s" } -> "rating:Safe, "
            else -> ""
        }

        val medScore = posts.map { it.stats.score.total }.sorted().getOrNull(posts.size / 2) ?: -99999
        val score = if (medScore != -99999) "score:>${medScore - 1}, " else ""

        val tags = posts.flatMap { post ->
            listOf(
                post.tags["lore"].orEmpty().map { "lore" to it },
                post.tags["general"].orEmpty().map { "general" to it },
                post.tags["species"].orEmpty().map { "species" to it },
                post.tags["character"].orEmpty().map { "character" to it },
                post.tags["copyright"].orEmpty().map { "copyright" to it },
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

        return manga.apply {
            url = pool.id.toString()
            title = pool.name.replace("_", " ")
            description = MarkdownDescription.Builder()
                .description(pool.description)
                .build()
            status = when (pool.isActive) {
                true -> SManga.ONGOING
                false -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = ("$rating$score" + tags.joinToString()).ifEmpty { null }
            author = artists.filter { it !in tagFilter }.joinToString().ifEmpty { null }
        }
    }

    private suspend fun chapterListParse(pool: Pool): List<SChapter> {
        val postIds = pool.postIds
        val title = pool.name.replace("_", " ")

        if (postIds.isEmpty()) return emptyList()

        val betterDetailsPref = preferences.betterDetailsPref
        val splitChaptersPref = preferences.splitChaptersPref
            .takeIf { preferences.splitChaptersPref != "chapters" || betterDetailsPref } ?: "merged"

        return when (splitChaptersPref) {
            "chapters" -> {
                val posts = batchFetchPosts(postIds)
                val poolIds = posts.flatMap { it.poolIds }.toSet() - pool.id
                val subPools = batchFetchPools(poolIds.toList())
                    .associateBy { it.id }
                    .filterValues { it.postIds.size <= pool.postIds.size }

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
                                date_upload = Instant.tryParse(subPool.updatedAt)
                            }
                        }
                        else -> SChapter.create().apply {
                            name = "Post #${post.id}"
                            url = "/posts/${post.id}"
                            chapter_number = (++n).toFloat()
                            date_upload = Instant.tryParse(post.createdAt)
                        }
                    }
                }.reversed().takeIf { it.size / 2 <= usedPools.size } ?: listOf(
                    SChapter.create().apply {
                        name = "​$title (${pool.postIds.size} pages)"
                        url = "/pools/${pool.id}"
                        chapter_number = 1f
                        date_upload = Instant.tryParse(pool.updatedAt)
                    },
                )
            }
            "posts" -> {
                postIds.mapIndexed { index, postId ->
                    SChapter.create().apply {
                        name = "Post #$postId"
                        url = "/posts/$postId"
                        chapter_number = (index + 1).toFloat()
                        date_upload = Instant.tryParse(pool.updatedAt)
                    }
                }.reversed()
            }
            "merged" -> {
                listOf(
                    SChapter.create().apply {
                        name = "Pool #${pool.id} (${postIds.size} pages)"
                        url = "/pools/${pool.id}"
                        chapter_number = 1f
                        date_upload = Instant.tryParse(pool.updatedAt)
                    },
                )
            }
            else -> emptyList()
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()

        val accountBlockedTags: List<List<String>> = if (preferences.accountBlockedTagsPref) {
            fetchAccountBlockedTags()
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.split(WHITESPACE_REGEX) }
        } else {
            emptyList()
        }

        val postIds = when (chapterUrl.pathSegments.getOrNull(0)) {
            "posts" -> listOf(chapterUrl.pathSegments.last().toInt())
            "pools" -> {
                val poolId = chapterUrl.pathSegments.last()
                client.get("$baseUrl/pools/$poolId.json", apiHeaders(), Fetch.XHR).parseAs<Pool>().postIds
            }
            else -> throw Exception("Unsupported chapter url")
        }

        if (postIds.isEmpty()) return emptyList()

        val posts = batchFetchPosts(postIds)
        val postMap = posts.associateBy { it.id }

        return postIds.mapIndexed { index, postId ->
            val post = postMap[postId]
            val imageUrl = when {
                post == null || post.flags.deleted -> DELETED_PLACEHOLDER
                isBlocked(post, accountBlockedTags) -> BLOCKED_PLACEHOLDER
                else -> extractImageUrl(post) ?: NO_IMAGE_PLACEHOLDER
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getE621FilterList(preferences.categoryPref)

    override fun setupPreferenceScreen(screen: PreferenceScreen) = setupE621PreferenceScreen(screen)

    private fun isBlocked(post: Post, blockedTags: List<List<String>>): Boolean {
        if (blockedTags.isEmpty()) return false
        val allTags = post.tags.values.flatten()
        return blockedTags.any { group -> group.all { tag -> tag in allTags } }
    }

    private fun extractImageUrl(post: Post): String? = if (preferences.fullResolution && post.files.meta.ext !in VIDEO_EXTENSIONS) {
        post.files.original.url
    } else {
        post.files.sample.webp
    }

    private suspend fun batchFetchPosts(postIds: List<Int>): List<Post> {
        if (postIds.isEmpty()) return emptyList()

        return postIds.chunked(200).flatMap { chunk ->
            val tagQuery = "status:all id:" + chunk.joinToString(",")
            val url = "$baseUrl/posts.json".toHttpUrl().newBuilder()
                .addQueryParameter("tags", tagQuery)
                .addQueryParameter("limit", chunk.size.toString())
                .addQueryParameter("mode", "extended")
                .build()

            val data = client.get(url, apiHeaders(), Fetch.XHR)
                .parseAs<List<Post>>()

            data.sortedBy { chunk.indexOf(it.id) }
        }
    }

    private suspend fun batchFetchPools(poolIds: List<Int>): List<Pool> {
        if (poolIds.isEmpty()) return emptyList()

        return poolIds.distinct().chunked(100).flatMap { chunk ->
            val url = "$baseUrl/pools.json".toHttpUrl().newBuilder()
                .addQueryParameter("search[order]", "id_desc")
                .addQueryParameter("search[id]", chunk.joinToString(","))
                .addQueryParameter("limit", chunk.size.toString())
                .build()

            val data = client.get(url, apiHeaders(), Fetch.XHR)
                .parseAs<List<Pool>>()

            data.sortedBy { chunk.indexOf(it.id) }
        }
    }

    private suspend fun batchFetchPostSamples(postIds: List<Int>): Map<Int, String> = batchFetchPosts(postIds).mapNotNull { post ->
        post.files.preview.webp?.let { post.id to it }
    }.toMap()

    private suspend fun fetchAccountBlockedTags(): String {
        val username = preferences.usernamePref.trim()
        val apiKey = preferences.apiKeyPref.trim()
        if (username.isEmpty() || apiKey.isEmpty()) return ""

        val credentials = "$username:$apiKey"
        val cached = cachedAccountBlockedTags
        if (cached != null && cachedAccountBlockedTagsCredentials == credentials) {
            return cached
        }

        val blockedTags = client.get("$baseUrl/users/me.json", apiHeaders(), Fetch.XHR).parseAs<UserMeResponse>().blockedTags ?: ""

        cachedAccountBlockedTags = blockedTags
        cachedAccountBlockedTagsCredentials = credentials
        return blockedTags
    }
}
