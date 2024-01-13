package eu.kanade.tachiyomi.extension.en.anchira

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.en.anchira.AnchiraHelper.getPathFromUrl
import eu.kanade.tachiyomi.extension.en.anchira.AnchiraHelper.prepareTags
import eu.kanade.tachiyomi.network.GET
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.TimeUnit

class Anchira : HttpSource(), ConfigurableSource {
    override val name = "Anchira"

    override val baseUrl = "https://anchira.to"

    private val apiUrl = "$baseUrl/api/v1"

    private val libraryUrl = "$apiUrl/library"

    private val cdnUrl = "https://kisakisexo.xyz"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .addInterceptor { apiInterceptor(it) }
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder() = super.headersBuilder().add("X-Requested-With", "XMLHttpRequest")

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$libraryUrl?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = json.decodeFromString<LibraryResponse>(response.body.string())

        return MangasPage(
            data.entries.map {
                SManga.create().apply {
                    url = "/g/${it.id}/${it.key}"
                    title = it.title
                    thumbnail_url = "$cdnUrl/${it.id}/${it.key}/m/${it.thumbnailIndex + 1}"
                    artist = it.tags.filter { it.namespace == 1 }.joinToString(", ") { it.name }
                    author = it.tags.filter { it.namespace == 2 }.joinToString(", ") { it.name }
                    genre = prepareTags(it.tags)
                    update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                    status = SManga.COMPLETED
                }
            }.toList(),
            (data.page + 1) * data.limit < data.total,
        )
    }

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$libraryUrl?sort=32&page=$page", headers)

    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = libraryUrl.toHttpUrl().newBuilder()

        url.addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("s", query)
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

                    if (sum > 0) url.addQueryParameter("cat", sum.toString())
                }

                is SortFilter -> {
                    val sort = when (filter.state?.index) {
                        0 -> "1"
                        1 -> "2"
                        2 -> "4"
                        4 -> "32"
                        else -> ""
                    }

                    if (sort.isNotEmpty()) url.addQueryParameter("sort", sort)
                    if (filter.state?.ascending == true) url.addQueryParameter("order", "1")
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

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // Details

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$libraryUrl/${getPathFromUrl(manga.url)}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val data = json.decodeFromString<Entry>(response.body.string())

        return SManga.create().apply {
            url = "/g/${data.id}/${data.key}"
            title = data.title
            thumbnail_url =
                "$cdnUrl/${data.id}/${data.key}/b/${data.thumbnailIndex + 1}"
            artist = data.tags.filter { it.namespace == 1 }.joinToString(", ") { it.name }
            author = data.tags.filter { it.namespace == 2 }.joinToString(", ") { it.name }
            genre = prepareTags(data.tags)
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.COMPLETED
        }
    }

    override fun getMangaUrl(manga: SManga) = if (preferences.openSource) {
        val id = manga.url.split("/").reversed()[1].toInt()
        anchiraData.galleries.find { it.id == id }?.url ?: "$baseUrl${manga.url}"
    } else {
        "$baseUrl${manga.url}"
    }

    // Chapter

    override fun chapterListRequest(manga: SManga) =
        GET("$libraryUrl/${getPathFromUrl(manga.url)}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = json.decodeFromString<Entry>(response.body.string())

        return listOf(
            SChapter.create().apply {
                url = "/g/${data.id}/${data.key}"
                name = "Chapter"
                date_upload = data.publishedAt * 1000
                chapter_number = 1f
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/g/${getPathFromUrl(chapter.url)}"

    // Page List

    override fun pageListRequest(chapter: SChapter) =
        GET("$libraryUrl/${getPathFromUrl(chapter.url)}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val data = json.decodeFromString<Entry>(response.body.string())
        val imageData = getImageData(data)

        return imageData.names.mapIndexed { i, name ->
            Page(
                i,
                imageUrl = "$cdnUrl/${imageData.id}/${imageData.key}/${imageData.hash}/b/$name",
            )
        }
    }

    private fun getImageData(entry: Entry): ImageData {
        val keys = anchiraData.galleries.find { it.id == entry.id }

        if (keys != null) {
            return ImageData(keys.id, keys.key, keys.hash, keys.names)
        }

        try {
            val response =
                client.newCall(GET("$libraryUrl/${entry.id}/${entry.key}/data", headers)).execute()
            val body = response.body

            return json.decodeFromString(response.body.string())
        } catch (_: IOException) {
            throw IOException("Complete a Captcha in the site to continue")
        }
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!.replace("/b/", "/${preferences.imageQuality}/"), headers)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used")

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
            setEnabled(false)
        }

        val openSourcePref = SwitchPreferenceCompat(screen.context).apply {
            key = OPEN_SOURCE_PREF
            title = "Open in FAKKU in WebView"
            summary =
                "Enable to open the search the book in FAKKU when opening the manga or chapter in WebView. If only one result exists, it will open that one."
            setDefaultValue(false)
        }

        screen.addPreference(imageQualityPref)
        screen.addPreference(openSourcePref)
    }

    override fun getFilterList() = FilterList(
        CategoryGroup(),
        SortFilter(),
        FavoritesFilter(),
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
        arrayOf("Title", "Pages", "Date published", "Date uploaded", "Popularity"),
        Selection(2, false),
    )

    private val SharedPreferences.imageQuality
        get() = getString(IMAGE_QUALITY_PREF, "b")!!

    private val SharedPreferences.openSource
        get() = getBoolean(OPEN_SOURCE_PREF, false)

    private fun apiInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestUrl = request.url.toString()

        return if (requestUrl.contains("/api/v1")) {
            val newRequestBuilder = request.newBuilder()

            if (requestUrl.contains(Regex("/\\d+/\\S+"))) {
                newRequestBuilder.header(
                    "Referer",
                    requestUrl.replace(libraryUrl, "$baseUrl/g"),
                )
            } else if (requestUrl.contains("user/favorites")) {
                newRequestBuilder.header(
                    "Referer",
                    requestUrl.replace("$apiUrl/user/favorites", "$baseUrl/favorites"),
                )
            } else {
                newRequestBuilder.header("Referer", requestUrl.replace(libraryUrl, baseUrl))
            }

            chain.proceed(newRequestBuilder.build())
        } else {
            chain.proceed(request)
        }
    }

    private fun isLoggedIn() = client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).any {
        it.name == "session"
    }

    private val anchiraData by lazy {
        client.newCall(GET(DATA_JSON, headers)).execute()
            .use { json.decodeFromStream<AnchiraData>(it.body.byteStream()) }
    }

    companion object {
        private const val IMAGE_QUALITY_PREF = "image_quality"
        private const val OPEN_SOURCE_PREF = "use_manga_source"
        private const val DATA_JSON =
            "https://gist.githubusercontent.com/LetrixZ/2b559cc5829d1c221c701e02ecd81411/raw/site_data.json"
    }
}
