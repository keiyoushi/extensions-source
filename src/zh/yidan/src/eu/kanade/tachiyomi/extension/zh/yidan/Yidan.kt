package eu.kanade.tachiyomi.extension.zh.yidan

import android.app.Application
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class Yidan : HttpSource(), ConfigurableSource {
    override val name get() = "一耽女孩"
    override val lang get() = "zh"
    override val supportsLatest get() = true

    override val baseUrl: String

    init {
        val mirrors = MIRRORS
        val index = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
            .getString(MIRROR_PREF, "0")!!.toInt().coerceAtMost(mirrors.size - 1)
        baseUrl = "https://" + mirrors[index]
    }

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", System.getProperty("http.agent")!!)

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/prod-api/app-api/vv/mh-list/page?mhcate=2&pageSize=50&pageNo=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val listing: ListingDto = response.parseAs()
        val mangas = listing.list.map { it.toSManga(baseUrl) }
        val hasNextPage = run {
            val url = response.request.url
            val pageSize = url.queryParameter("pageSize")!!.toInt()
            val pageNumber = url.queryParameter("pageNo")!!.toInt()
            pageSize * pageNumber < listing.totalCount
        }
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/prod-api/app-api/vv/mh-list/page?mhcate=4&pageSize=50&pageNo=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/prod-api/app-api/vv/mh-list/page".toHttpUrl().newBuilder()
            .apply { if (query.isNotBlank()) addQueryParameter("word", query) }
            .apply { parseFilters(filters, this) }
            .addEncodedQueryParameter("pageSize", "50")
            .addEncodedQueryParameter("pageNo", page.toString())
            .build()
        return Request.Builder().url(url).headers(headers).build()
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // for WebView
    override fun mangaDetailsRequest(manga: SManga) =
        GET("$baseUrl/#/pages/detail/detail?id=${manga.url}")

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val request = GET("$baseUrl/prod-api/app-api/vv/mh-list/get?id=${manga.url}", headers)
        return client.newCall(request).asObservableSuccess().map { mangaDetailsParse(it) }
    }

    override fun mangaDetailsParse(response: Response) =
        response.parseAs<MangaDto>().toSManga(baseUrl)

    override fun chapterListRequest(manga: SManga) =
        GET("$baseUrl/prod-api/app-api/vv/mh-episodes/list?mhid=${manga.url}", headers)

    override fun chapterListParse(response: Response) =
        response.parseAs<List<ChapterDto>>().map { it.toSChapter() }

    // for WebView
    override fun pageListRequest(chapter: SChapter): Request {
        val (mangaId, chapterIndex) = chapter.url.split("/")
        return GET("$baseUrl/#/pages/read/read?no=$chapterIndex&id=$mangaId")
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val (mangaId, chapterIndex) = chapter.url.split("/")
        val url = "$baseUrl/prod-api/app-api/vv/mh-episodes/get?jiNo=$chapterIndex&mhid=$mangaId"
        return client.newCall(GET(url, headers)).asObservableSuccess().map { pageListParse(it) }
    }

    override fun pageListParse(response: Response) =
        response.parseAs<PageListDto>().images.mapIndexed { index, url ->
            val imageUrl = if (url.startsWith("http")) url else baseUrl + url
            Page(index, imageUrl = imageUrl)
        }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream<ResponseDto<T>>(body.byteStream()).data
    }

    override fun getFilterList() = getFilterListInternal()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            val mirrors = MIRRORS
            key = MIRROR_PREF
            title = "镜像网址（重启生效）"
            summary = "%s"
            entries = mirrors
            entryValues = Array(mirrors.size, Int::toString)
            setDefaultValue("0")
        }.let(screen::addPreference)
    }

    companion object {
        private const val MIRROR_PREF = "MIRROR"
        private val MIRRORS get() = arrayOf("ydan.cc", "yidan.one", "yidan.in", "yidan.info")
    }
}
