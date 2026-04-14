package eu.kanade.tachiyomi.extension.all.e621

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class E621 :
    HttpSource(),
    ConfigurableSource {

    override val name: String = "e621"
    override val baseUrl: String = "https://e621.net"
    override val lang: String = "all"
    override val supportsLatest: Boolean = true

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
        val jsonArray = response.parseAs<JsonArray>()

        val thumbnailMap = batchFetchPostSamples(
            jsonArray.mapNotNull { pool ->
                pool.jsonObject["post_ids"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonPrimitive
                    ?.int
            },
        ).takeIf { it.isNotEmpty() } ?: emptyMap()

        val poolList = jsonArray.map { poolElement ->
            val pool = poolElement.jsonObject
            val posts = pool["post_ids"]?.jsonArray ?: JsonArray(emptyList())

            SManga.create().apply {
                url = "/pools/${pool["id"]?.jsonPrimitive?.int}"
                title = pool["name"]?.jsonPrimitive?.content?.replace("_", " ") ?: "Unknown Pool"
                thumbnail_url = posts.firstOrNull()?.jsonPrimitive?.int
                    ?.let { thumbnailMap[it] }
                    ?: ""
            }
        }

        return MangasPage(poolList, poolList.size >= 24)
    }

    // Details
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val poolId = manga.url.split("/").last()

        return client.newCall(GET("$baseUrl/pools/$poolId.json", headers))
            .asObservableSuccess()
            .map { response ->
                val pool = response.parseAs<JsonObject>()
                manga.apply {
                    title = pool["name"]?.jsonPrimitive?.content?.replace("_", " ") ?: ""
                    description = pool["description"]?.jsonPrimitive?.content ?: ""

                    val postIds = pool["post_ids"]?.jsonArray?.map { it.jsonPrimitive.int } ?: emptyList()
                    val authors = if (postIds.isNotEmpty()) {
                        val postsResponse = client.newCall(
                            GET("$baseUrl/posts.json?tags=pool:$poolId&limit=50", headers), // Limit to 50 to reduce time
                        ).execute()

                        val posts = postsResponse.parseAs<JsonObject>()["posts"]?.jsonArray ?: JsonArray(emptyList())

                        posts.flatMap { post ->
                            post.jsonObject["tags"]?.jsonObject?.get("artist")?.jsonArray
                                ?.mapNotNull { it.jsonPrimitive.content }
                                ?: emptyList()
                        }
                            .filterNot { it in artistFilter }
                            .distinct()
                    } else {
                        emptyList()
                    }
                    author = authors.joinToString(", ")
                    artist = authors.joinToString(", ")

                    status = when (pool["is_active"]?.jsonPrimitive?.boolean) {
                        true -> SManga.ONGOING
                        false -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }

                    genre = pool["category"]?.jsonPrimitive?.content
                }
            }
    }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException("Not used")

    // Chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val poolId = manga.url.split("/").last()

        return client.newCall(GET("$baseUrl/pools/$poolId.json", headers))
            .asObservableSuccess()
            .map { response ->
                val pool = response.parseAs<JsonObject>()
                val postIds = pool["post_ids"]?.jsonArray?.map { it.jsonPrimitive.int } ?: emptyList()
                val updatedAt = pool["updated_at"]?.jsonPrimitive?.content

                if (preferences.splitChaptersPref && postIds.isNotEmpty()) {
                    postIds.mapIndexed { index, postId ->
                        SChapter.create().apply {
                            name = "Post ${index + 1}"
                            url = "/posts/$postId"
                            chapter_number = (index + 1).toFloat()
                            date_upload = if (index == 0) updatedAt?.let { parseDate(it) } ?: 0L else 0L
                        }
                    }.reversed()
                } else {
                    listOf(
                        SChapter.create().apply {
                            name = "Pool (${postIds.size} pages)"
                            url = "/pools/$poolId"
                            date_upload = updatedAt?.let { parseDate(it) } ?: 0L
                        },
                    )
                }
            }
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url

        // Check if this is a single post chapter (split chapters mode)
        if (url.pathSegments.getOrNull(0) == "posts") {
            val postId = url.pathSegments.last().toIntOrNull() ?: return emptyList()
            val post = batchFetchPosts(listOf(postId)).firstOrNull()
            val imageUrl = when {
                post == null -> "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Deleted" // Not returned by API
                isPostDeleted(post) -> "https://placehold.co/256x256/cccccc/f66151.jpg?text=Post%20Deleted"
                else -> extractImageUrl(post) ?: "https://placehold.co/256x256/cccccc/f66151.jpg?text=No%20Image"
            }
            return listOf(Page(0, imageUrl = imageUrl))
        }

        // Otherwise, it's a pool with all pages
        val poolId = url.pathSegments.last()
        val poolResponse = client.newCall(
            GET("$baseUrl/pools.json?search[id]=$poolId&limit=1", headers),
        ).execute()

        val postIds = poolResponse.parseAs<JsonArray>()
            .firstOrNull()
            ?.jsonObject
            ?.get("post_ids")
            ?.jsonArray
            ?.map { it.jsonPrimitive.int }
            ?: return emptyList()

        val posts = batchFetchPosts(postIds)
        val postMap = posts.associateBy { it["id"]?.jsonPrimitive?.int }

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

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Search by pool name"),
        DescriptionFilter(),
        OrderFilter(),
        CategoryFilter(getDefaultCategoryIndex()),
        ActiveOnlyFilter(),
    )

    private fun getDefaultCategoryIndex(): Int = when (preferences.categoryPref) {
        "series" -> 1
        "collection" -> 2
        else -> 0 // "" (both) maps to "Any"
    }

    private class OrderFilter :
        UriPartFilter(
            "Order by",
            arrayOf(
                Pair("Recently Updated", "updated_at"),
                Pair("Most Posts", "post_count"),
                Pair("Name (A-Z)", "name"),
                Pair("Newest First", "created_at"),
            ),
        )

    private class CategoryFilter(defaultIndex: Int = 0) :
        UriPartFilter(
            "Category",
            arrayOf(
                Pair("Any", ""),
                Pair("Series", "series"),
                Pair("Collection", "collection"),
            ),
            defaultIndex,
        )

    private class ActiveOnlyFilter : Filter.CheckBox("Active pools only", false)

    private class DescriptionFilter : Filter.Text("Description contains")

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>, defaultIndex: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultIndex) {
        fun toUriPart() = vals[state].second
    }

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = CATEGORY_PREF
            title = "Pool category filter for Popular and Latest"
            entries = arrayOf("Series only", "Collections only", "Both")
            entryValues = arrayOf("series", "collection", "")
            setDefaultValue("series")
            summary = "%s"
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SPLIT_CHAPTERS_PREF
            title = "Split posts into individual chapters"
            summary = "Each post in a pool will be shown as a separate chapter instead of one merged chapter"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private val SharedPreferences.categoryPref: String
        get() = getString(CATEGORY_PREF, "series")!!

    private val SharedPreferences.splitChaptersPref: Boolean
        get() = getBoolean(SPLIT_CHAPTERS_PREF, false)

    companion object {
        private const val CATEGORY_PREF = "category_filter"
        private const val SPLIT_CHAPTERS_PREF = "split_chapters"
    }

    // Helpers

    private fun isPostDeleted(post: JsonObject): Boolean = post["flags"]?.jsonObject?.get("deleted")?.jsonPrimitive?.boolean == true

    private fun extractThumbnailUrl(post: JsonObject): String? {
        // Preview (smallest, fastest to load)
        post["preview"]?.jsonObject?.get("url")?.jsonPrimitive?.content?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        // Sample
        post["sample"]?.jsonObject?.get("url")?.jsonPrimitive?.content?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        // Full Resolution
        post["file"]?.jsonObject?.get("url")?.jsonPrimitive?.content?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        return null
    }

    private fun extractImageUrl(post: JsonObject): String? {
        // Full Resolution (best quality for reading)
        post["file"]?.jsonObject?.get("url")?.jsonPrimitive?.content?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        // Sample
        post["sample"]?.jsonObject?.get("url")?.jsonPrimitive?.content?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        // Preview
        post["preview"]?.jsonObject?.get("url")?.jsonPrimitive?.content?.let {
            if (it != "null" && it.isNotEmpty()) return it
        }

        return null
    }

    private fun batchFetchPosts(postIds: List<Int>): List<JsonObject> {
        if (postIds.isEmpty()) return emptyList()

        return postIds.chunked(40).flatMap { chunk ->
            runCatching {
                val tagQuery = chunk.joinToString(" ") { "~id:$it" }
                val url = "$baseUrl/posts.json".toHttpUrl().newBuilder()
                    .addQueryParameter("tags", tagQuery)
                    .addQueryParameter("limit", chunk.size.toString())
                    .build()

                val data = client.newCall(GET(url, headers)).execute()
                    .parseAs<JsonObject>()

                data["posts"]?.jsonArray.orEmpty().map { it.jsonObject }
            }.getOrDefault(emptyList())
        }
    }

    private fun batchFetchPostSamples(postIds: List<Int>): Map<Int, String> {
        if (postIds.isEmpty()) return emptyMap()

        return batchFetchPosts(postIds).mapNotNull { post ->
            val id = post["id"]?.jsonPrimitive?.int ?: return@mapNotNull null
            extractThumbnailUrl(post)?.let { id to it }
        }.toMap()
    }

    private fun parseDate(dateStr: String): Long = try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
        format.parse(dateStr)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}
