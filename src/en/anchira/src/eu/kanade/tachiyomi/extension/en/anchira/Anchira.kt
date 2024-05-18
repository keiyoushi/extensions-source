package eu.kanade.tachiyomi.extension.en.anchira

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.en.anchira.AnchiraHelper.createChapter
import eu.kanade.tachiyomi.extension.en.anchira.AnchiraHelper.getCdn
import eu.kanade.tachiyomi.extension.en.anchira.AnchiraHelper.getPathFromUrl
import eu.kanade.tachiyomi.extension.en.anchira.AnchiraHelper.prepareTags
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.min

class Anchira : HttpSource(), ConfigurableSource {
    override val name = "Anchira"

    override val baseUrl = "https://anchira.to"

    private val apiUrl = baseUrl.replace("://", "://api.")

    private val libraryUrl = "$apiUrl/library"

    private val cdnUrl = "https://kisakisexo.xyz"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .addInterceptor { resampledInterceptor(it) }
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$libraryUrl?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        // Ugly but it works
        anchiraData.isNotEmpty()

        val data = json.decodeFromString<LibraryResponse>(response.body.string())

        return MangasPage(
            data.entries.map {
                SManga.create().apply {
                    url = "/g/${it.id}/${it.key}"
                    title = it.title
                    thumbnail_url = "$cdnUrl/${it.id}/${it.key}/m/${it.cover?.name}"
                    val art = it.tags.filter { it.namespace == 1 }.joinToString(", ") { it.name }
                        .ifEmpty { null }
                    artist = art
                    author = it.tags.filter { it.namespace == 2 }.joinToString(", ") { it.name }
                        .ifEmpty { art }
                    genre = prepareTags(it.tags, preferences.useTagGrouping)
                    update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                    status = SManga.COMPLETED
                }
            }.toList(),
            data.page * data.limit < data.total,
        )
    }

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$libraryUrl?sort=32&page=$page", headers)

    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)

    // Search

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(SLUG_SEARCH_PREFIX)) {
            // url deep link
            val idKey = query.substringAfter(SLUG_SEARCH_PREFIX)
            val manga = SManga.create().apply { this.url = "/g/$idKey" }
            fetchMangaDetails(manga).map {
                MangasPage(listOf(it), false)
            }
        } else if (query.startsWith(SLUG_BUNDLE_PREFIX)) {
            // bundle entries as chapters
            val url = applyFilters(
                page,
                query.substringAfter(SLUG_BUNDLE_PREFIX),
                filters,
            ).removeAllQueryParameters("page")
            val manga = SManga.create()
                .apply { this.url = "?${url.build().query}" }
            fetchMangaDetails(manga).map {
                MangasPage(listOf(it), false)
            }
        } else {
            // regular filtering without text search
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map(::searchMangaParse)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET(applyFilters(page, query, filters).build(), headers)

    private fun applyFilters(page: Int, query: String, filters: FilterList): HttpUrl.Builder {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val trendingFilter = filterList.findInstance<TrendingFilter>()
        val sortTrendingFilter = filters.findInstance<SortTrendingFilter>()
        var url = libraryUrl.toHttpUrl().newBuilder()

        if (trendingFilter?.state == true) {
            val interval = when (sortTrendingFilter?.state) {
                1 -> "3"
                else -> ""
            }

            if (interval.isNotBlank()) url.setQueryParameter("interval", interval)

            url = url.toString().replace("library", "trending").toHttpUrl()
                .newBuilder()
        } else {
            if (query.isNotBlank()) {
                url.setQueryParameter("s", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is CategoryGroup -> {
                        var sum = 0

                        filter.state.forEach { category ->
                            when (category.name) {
                                "Manga" -> if (category.state) sum = sum or 1
                                "Doujinshi" -> if (category.state) sum = sum or 2
                                "Illustration" -> if (category.state) sum = sum or 4
                            }
                        }

                        if (sum > 0) url.setQueryParameter("cat", sum.toString())
                    }

                    is SortFilter -> {
                        val sort = when (filter.state?.index) {
                            0 -> "1"
                            1 -> "2"
                            2 -> "4"
                            4 -> "32"
                            else -> ""
                        }

                        if (sort.isNotEmpty()) url.setQueryParameter("sort", sort)
                        if (filter.state?.ascending == true) url.setQueryParameter("order", "1")
                    }

                    is FavoritesFilter -> {
                        if (filter.state) {
                            if (!isLoggedIn()) {
                                throw IOException("No login cookie found")
                            }

                            url = url.toString().replace("library", "user/favorites").toHttpUrl()
                                .newBuilder()
                        }
                    }

                    else -> {}
                }
            }
        }

        if (page > 1) {
            url.setQueryParameter("page", page.toString())
        }

        return url
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return if (manga.url.startsWith("?")) {
            GET(libraryUrl + manga.url, headers)
        } else {
            GET("$libraryUrl/${getPathFromUrl(manga.url)}", headers)
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return if (response.request.url.pathSegments.count() == libraryUrl.toHttpUrl().pathSegments.count()) {
            val manga = latestUpdatesParse(response).mangas.first()
            val query = response.request.url.queryParameter("s")
            val cleanTitle = CHAPTER_SUFFIX_RE.replace(manga.title, "").trim()
            manga.apply {
                url = "?${response.request.url.query}"
                description = "Bundled from $query"
                title = "[Bundle] $cleanTitle"
                update_strategy = UpdateStrategy.ALWAYS_UPDATE
            }
        } else {
            val data = json.decodeFromString<Entry>(response.body.string())

            SManga.create().apply {
                url = "/g/${data.id}/${data.key}"
                title = data.title
                thumbnail_url =
                    "$cdnUrl/${data.id}/${data.key}/l/${data.images[data.thumbnailIndex].name}"
                val art = data.tags.filter { it.namespace == 1 }.joinToString(", ") { it.name }
                    .ifEmpty { null }
                artist = art
                author = data.tags.filter { it.namespace == 2 }.joinToString(", ") { it.name }
                    .ifEmpty { art }
                genre = prepareTags(data.tags, preferences.useTagGrouping)
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                status = SManga.COMPLETED
            }
        }
    }

    override fun getMangaUrl(manga: SManga) =
        if (preferences.openSource && !manga.url.startsWith("?")) {
            val id = manga.url.split("/").reversed()[1].toInt()
            anchiraData.find { it.id == id }?.url ?: "$baseUrl${manga.url}"
        } else {
            "$baseUrl${manga.url}"
        }

    // Chapter

    override fun chapterListRequest(manga: SManga): Request {
        return if (manga.url.startsWith("?")) {
            GET(libraryUrl + manga.url, headers)
        } else {
            GET("$libraryUrl/${getPathFromUrl(manga.url)}", headers)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterList = mutableListOf<SChapter>()
        if (response.request.url.pathSegments.count() == libraryUrl.toHttpUrl().pathSegments.count()) {
            var results = json.decodeFromString<LibraryResponse>(response.body.string())
            val pages = min(5, ceil((results.total.toFloat() / results.limit)).toInt())
            for (page in 1..pages) {
                results.entries.forEach { data ->
                    chapterList.add(
                        createChapter(data, anchiraData),
                    )
                }
                if (page < pages) {
                    results = json.decodeFromString<LibraryResponse>(
                        client.newCall(
                            GET(
                                response.request.url.newBuilder()
                                    .setQueryParameter("page", (page + 1).toString()).build(),
                                headers,
                            ),
                        ).execute().body.string(),
                    )
                }
            }
        } else {
            val data = json.decodeFromString<Entry>(response.body.string())
            chapterList.add(
                createChapter(data, anchiraData),
            )
        }
        return chapterList
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/g/${getPathFromUrl(chapter.url)}"

    // Page List

    override fun pageListRequest(chapter: SChapter) =
        GET("$libraryUrl/${getPathFromUrl(chapter.url)}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val data = json.decodeFromString<Entry>(response.body.string())
        val imageData = getImageData(data)

        return data.images.mapIndexed { i, image ->
            Page(
                i,
                imageUrl = "${getCdn(i)}/${imageData.id}/${imageData.key}/${imageData.hash}/b/${image.name}",
            )
        }
    }

    private fun getImageData(entry: Entry): ImageData {
        val keys = anchiraData.find { it.id == entry.id }

        if (keys?.key != null && keys.hash != null) {
            return ImageData(keys.id, keys.key, keys.hash)
        }

        try {
            val response =
                client.newCall(GET("$libraryUrl/${entry.id}/${entry.key}/data", headers)).execute()

            return json.decodeFromString(response.body.string())
        } catch (_: IOException) {
            throw IOException("Complete a Captcha in the site to continue")
        }
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!.replace("/b/", "/${preferences.imageQuality}/"), headers)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Settings

    @SuppressLint("SetTextI18n")
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val imageQualityPref = ListPreference(screen.context).apply {
            key = IMAGE_QUALITY_PREF
            title = "Image quality"
            entries = arrayOf("Original", "Resampled")
            entryValues = arrayOf("a", "b")
            setDefaultValue("b")
            summary = "%s"
        }

        val openSourcePref = SwitchPreferenceCompat(screen.context).apply {
            key = OPEN_SOURCE_PREF
            title = "Open source website in WebView"
            summary =
                "Enable to open the original source website of the gallery (if available) instead of Anchira."
            setDefaultValue(false)
        }

        val useTagGrouping = SwitchPreferenceCompat(screen.context).apply {
            key = USE_TAG_GROUPING
            title = "Group tags"
            summary =
                "Enable to group tags together by artist, circle, parody, magazine and general tags"
            setDefaultValue(false)
        }

        screen.addPreference(imageQualityPref)
        screen.addPreference(openSourcePref)
        screen.addPreference(useTagGrouping)
    }

    override fun getFilterList() = FilterList(
        CategoryGroup(),
        SortFilter(),
        FavoritesFilter(),
        Filter.Separator(),
        Filter.Header("Others are ignored if trending only"),
        TrendingFilter(),
        SortTrendingFilter(),
    )

    private class CategoryFilter(name: String) : Filter.CheckBox(name, false)

    private class FavoritesFilter : Filter.CheckBox(
        "Show only my favorites",
    )

    private class CategoryGroup : Filter.Group<CategoryFilter>(
        "Categories",
        listOf("Manga", "Doujinshi", "Illustration").map { CategoryFilter(it) },
    )

    private class SortFilter : Filter.Sort(
        "Sort",
        arrayOf("Title", "Pages", "Date uploaded", "Date published", "Popularity"),
        Selection(2, false),
    )

    private class TrendingFilter : Filter.CheckBox(
        "Show only trending",
    )

    private class SortTrendingFilter : PartFilter(
        "Sort By",
        arrayOf("Trending: Weekly", "Trending: Monthly"),
    )

    private open class PartFilter(displayName: String, value: Array<String>) :
        Filter.Select<String>(displayName, value)

    private val SharedPreferences.imageQuality
        get() = getString(IMAGE_QUALITY_PREF, "b")!!

    private val SharedPreferences.openSource
        get() = getBoolean(OPEN_SOURCE_PREF, false)

    private val SharedPreferences.useTagGrouping
        get() = getBoolean(USE_TAG_GROUPING, false)

    private fun resampledInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        return if (url.contains("sexo.xyz")) {
            val response = chain.proceed(request)

            if (response.isSuccessful) {
                return response
            } else if (url.contains("/b/")) {
                return chain.proceed(request.newBuilder().url(url.replace("/b/", "/a/")).build())
            }

            throw IOException("An error occurred while loading the image - ${response.code}")
        } else {
            chain.proceed(request)
        }
    }

    private fun isLoggedIn() = client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).any {
        it.name == "session"
    }

    private val anchiraData by lazy {
        client.newCall(GET(DATA_JSON, headers)).execute()
            .use { json.decodeFromStream<List<EntryKey>>(it.body.byteStream()) }
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    companion object {
        const val SLUG_SEARCH_PREFIX = "id:"
        private const val SLUG_BUNDLE_PREFIX = "bundle:"
        private const val IMAGE_QUALITY_PREF = "image_quality"
        private const val OPEN_SOURCE_PREF = "use_manga_source"
        private const val USE_TAG_GROUPING = "use_tag_grouping"
        private const val DATA_JSON =
            "https://raw.githubusercontent.com/LetrixZ/gallery-data/main/extension_data.min.json"
    }
}

val CHAPTER_SUFFIX_RE =
    Regex("\\W*(?:Ch\\.?|Chapter|Part|Vol\\.?|Volume|#)?\\W?(?<!20\\d{2}-?)\\b[\\d.]{1,4}\\W?")
