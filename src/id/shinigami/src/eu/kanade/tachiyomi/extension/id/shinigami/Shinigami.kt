package eu.kanade.tachiyomi.extension.id.shinigami

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Shinigami : ConfigurableSource, HttpSource() {
    // moved from Reaper Scans (id) to Shinigami (id)
    override val id = 3411809758861089969

    override val name = "Shinigami"

    override val baseUrl by lazy { getPrefBaseUrl() }

    private var defaultBaseUrl = "https://app.shinigami.asia"

    private val apiUrl = "https://api.shngm.io"

    private val cdnUrl = "https://storage.shngm.id"

    override val lang = "id"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val apiHeaders: Headers by lazy { apiHeadersBuilder().build() }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder().apply {
                removeAll("X-Requested-With")
            }.build()

            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("X-Requested-With", randomString((1..20).random())) // added for webview, and removed in interceptor for normal use

    private fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    private fun apiHeadersBuilder(): Headers.Builder = headersBuilder()
        .add("Accept", "application/json")
        .add("DNT", "1")
        .add("Origin", baseUrl)
        .add("Sec-GPC", "1")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/$API_BASE_PATH/manga/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")
            .addQueryParameter("sort", "popularity")
            .toString()

        return GET(url, apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val rootObject = response.parseAs<ShinigamiBrowseDto>()
        val projectList = rootObject.data.map(::popularMangaFromObject)

        val hasNextPage = rootObject.meta.page < rootObject.meta.totalPage

        return MangasPage(projectList, hasNextPage)
    }

    private fun popularMangaFromObject(obj: ShinigamiBrowseDataDto): SManga = SManga.create().apply {
        title = obj.title.toString()
        thumbnail_url = obj.thumbnail
        url = "$apiUrl/$API_BASE_PATH/manga/detail/" + obj.mangaId
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/$API_BASE_PATH/manga/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")
            .addQueryParameter("sort", "latest")
            .toString()

        return GET(url, apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/$API_BASE_PATH/manga/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "30")

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        // TODO: search by tag/genre/status/etc

        return GET(url.toString(), apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/series/" + manga.url.substringAfter("manga/detail/")
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        // Migration from old web urls to the new api based
        if (manga.url.startsWith("/series/")) {
            throw Exception("Migrate dari $name ke $name (ekstensi yang sama)")
        }

        return GET(manga.url, apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaDetailsResponse = response.parseAs<ShinigamiMangaDetailDto>()
        val mangaDetails = mangaDetailsResponse.data

        return SManga.create().apply {
            author = mangaDetails.taxonomy["Author"]?.joinToString(", ") { it.name }.orEmpty()
            artist = mangaDetails.taxonomy["Artist"]?.joinToString(", ") { it.name }.orEmpty()
            status = mangaDetails.status.toStatus()
            description = mangaDetails.description

            val genres = mangaDetails.taxonomy["Genre"]?.joinToString(", ") { it.name }.orEmpty()
            val type = mangaDetails.taxonomy["Format"]?.joinToString(", ") { it.name }.orEmpty()
            genre = listOf(genres, type).filter { it.isNotBlank() }.joinToString(", ")
        }
    }

    private fun Int.toStatus(): Int {
        return when (this) {
            1 -> SManga.ONGOING
            2 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(
            "$apiUrl/$API_BASE_PATH/chapter/" + manga.url.substringAfter("manga/detail/") +
                "/list?page_size=3000",
            apiHeaders,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ShinigamiChapterListDto>()

        return result.chapterList.map(::chapterFromObject)
    }

    private fun chapterFromObject(obj: ShinigamiChapterListDataDto): SChapter = SChapter.create().apply {
        date_upload = obj.date.toDate() ?: 0
        name = "Chapter ${obj.name} ${obj.title}"
        url = "$apiUrl/$API_BASE_PATH/chapter/detail/" + obj.chapterId
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // Migration from old web urls to the new api based
        if (chapter.url.startsWith("/series/")) {
            throw Exception("Migrate dari $name ke $name (ekstensi yang sama)")
        }

        return GET(chapter.url, apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ShinigamiPageListDto>()

        return result.pageList.chapterPage.pages.mapIndexed { index, imageName ->
            Page(index = index, imageUrl = "$cdnUrl${result.pageList.chapterPage.path}$imageName")
        }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .add("DNT", "1")
            .add("referer", "$baseUrl/")
            .add("sec-fetch-dest", "empty")
            .add("Sec-GPC", "1")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body.string())
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER_V2.parse(this)?.time }.getOrNull()
            ?: runCatching { DATE_FORMATTER.parse(this)?.time }.getOrNull()
            ?: 0
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            this.setDefaultValue(defaultBaseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                true
            }
        }
        screen.addPreference(baseUrlPref)
    }

    private fun getPrefBaseUrl(): String =
        preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!.trimEnd('/')

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != defaultBaseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
        }
        private val DATE_FORMATTER_V2 by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
        }

        private const val API_BASE_PATH = "v1"
        private const val RESTART_APP = "Restart aplikasi untuk menerapkan perubahan."
        private const val BASE_URL_PREF_TITLE = "Ubah Domain"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY = "Untuk penggunaan sementara. Memperbarui ekstensi akan menghapus pengaturan. \n\n❗ Restart aplikasi untuk menerapkan perubahan. ❗"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
    }
}
