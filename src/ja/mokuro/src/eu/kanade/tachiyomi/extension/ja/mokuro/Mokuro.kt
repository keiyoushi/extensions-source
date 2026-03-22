package eu.kanade.tachiyomi.extension.ja.mokuro

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class Mokuro :
    HttpSource(),
    ConfigurableSource {

    override val name = "Mokuro"
    override val baseUrl = "https://mokuro.moe"
    override val lang = "ja"
    override val supportsLatest = false

    private val apiBaseUrl = "$baseUrl/catalog/api"

    override val client = network.client.newBuilder()
        .addInterceptor(CbzInterceptor())
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/catalog")

    private val preferences by getPreferencesLazy()

    // ===============================
    // Popular
    // ===============================

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = getLibrary().map { library ->
        MangasPage(library.series.map { it.toSManga(apiBaseUrl, useLatestVolumeCover) }, false)
    }

    // ===============================
    // Search
    // ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = getLibrary().map { library ->
        val mangas = library.series
            .filter { it.name.contains(query.trim(), ignoreCase = true) }
            .map { it.toSManga(apiBaseUrl, useLatestVolumeCover) }
        MangasPage(mangas, false)
    }

    // ===============================
    // Details
    // ===============================

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = getLibrary().map { library ->
        library.series.find { it.path == manga.url }?.toSManga(apiBaseUrl, useLatestVolumeCover)
            ?: throw Exception("Series not found")
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/catalog#${manga.url}"

    // ===============================
    // Chapters
    // ===============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = getLibrary().map { library ->
        val series = library.series.find { it.path == manga.url }
            ?: throw Exception("Series not found")

        series.volumes.asReversed().map { it.toSChapter(series) }
    }

    // ===============================
    // Pages
    // ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val (seriesPath, volumeName) = chapter.url.split("|", limit = 2)
        val url = "$baseUrl/mokuro-reader".toHttpUrl().newBuilder()
            .addPathSegment(seriesPath)
            .addPathSegment("$volumeName.mokuro")
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val mokuro = response.parseAs<MokuroDto>()
        val url = response.request.url
        val cbzUrl = url.newBuilder()
            .encodedPath(url.encodedPath.removeSuffix(".mokuro") + ".cbz")
            .build()

        return mokuro.pages.mapIndexed { index, page ->
            Page(index, imageUrl = "$cbzUrl#${page.imgPath}")
        }
    }

    // ===============================
    // Settings
    // ===============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_USE_LATEST_VOLUME_COVER
            title = "最新巻の表紙を使用"
            summary = "デフォルトのシリーズ表紙の代わりに、最新巻の表紙を漫画のサムネイルとして使用します。"
            setDefaultValue(PREF_USE_LATEST_VOLUME_COVER_DEFAULT)
        }.also(screen::addPreference)
    }

    private val useLatestVolumeCover: Boolean
        get() = preferences.getBoolean(PREF_USE_LATEST_VOLUME_COVER, PREF_USE_LATEST_VOLUME_COVER_DEFAULT)

    companion object {
        private const val PREF_USE_LATEST_VOLUME_COVER = "pref_use_latest_volume_cover"
        private const val PREF_USE_LATEST_VOLUME_COVER_DEFAULT = false
    }

    // ===============================
    // Helpers
    // ===============================

    @Volatile
    private var libraryCache: LibraryDto? = null
    private var libraryCacheTime = 0L
    private val cacheDuration = 10 * 60 * 1000L

    @Volatile
    private var inFlight: Observable<LibraryDto>? = null

    private fun updateLibraryCache(response: Response): LibraryDto {
        val now = System.currentTimeMillis()
        return response.parseAs<LibraryDto>().also {
            synchronized(this) {
                libraryCache = it
                libraryCacheTime = now
            }
        }
    }

    private fun getLibrary(): Observable<LibraryDto> {
        val now = System.currentTimeMillis()
        val cached = libraryCache

        if (cached != null && now - libraryCacheTime < cacheDuration) {
            return Observable.just(cached)
        }

        synchronized(this) {
            inFlight?.let { return it }

            val observable = client.newCall(GET("$apiBaseUrl/library", headers))
                .asObservableSuccess()
                .map { updateLibraryCache(it) }
                .doOnTerminate { inFlight = null }
                .cache()

            inFlight = observable
            return observable
        }
    }

    // ===============================
    // Unused
    // ===============================

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun mangaDetailsRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
