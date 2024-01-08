package eu.kanade.tachiyomi.extension.en.disasterscans

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class DisasterScans : HttpSource() {

    override val name = "Disaster Scans"

    override val lang = "en"

    override val versionId = 2

    override val baseUrl = "https://disasterscans.com"

    private val apiUrl = "https://api.disasterscans.com"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (url.fragment == "thumbnail") {
                val cdnUrl = preferences.getCdnUrl()
                val requestUrl = url.toString().substringBefore("=") + "="
                if (cdnUrl != requestUrl) {
                    val fileId = url.queryParameterValues("fileId").first()
                    return@addInterceptor chain.proceed(
                        request.newBuilder()
                            .url("$cdnUrl$fileId")
                            .build(),
                    )
                }
            }
            return@addInterceptor chain.proceed(request)
        }
        .rateLimit(1)
        .build()

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/comics/search/comics", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val comics = response.parseAs<List<ApiSearchComic>>()

        val cdnUrl = preferences.getCdnUrl()

        return MangasPage(comics.map { it.toSManga(cdnUrl) }, false)
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG)) {
            val url = "/comics/${query.substringAfter(PREFIX_SLUG)}"
            val manga = SManga.create().apply { this.url = url }
            client.newCall(mangaDetailsRequest(manga))
                .asObservableSuccess()
                .map { mangaDetailsParse(it).apply { this.url = url } }
                .map { MangasPage(listOf(it), false) }
        } else {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { searchMangaParse(it, query) }
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = popularMangaRequest(page)

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val comics = response.parseAs<List<ApiSearchComic>>()

        val cdnUrl = preferences.getCdnUrl()

        return comics
            .filter { it.ComicTitle.contains(query, true) }
            .map { it.toSManga(cdnUrl) }
            .let { MangasPage(it, false) }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$apiUrl${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val comic = response.parseAs<ApiComic>()

        return comic.toSManga(json, preferences.getCdnUrl())
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$apiUrl${manga.url.replace("comics", "chapters")}"
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.parseAs<List<ApiChapter>>()

        val mangaUrl = response.request.url.toString()
            .substringAfter(apiUrl)
            .replace("chapters", "comics")

        return chapters.map { it.toSChapter(mangaUrl) }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val chapterPages = document.select("#__NEXT_DATA__").html()
            .parseAs<NextData<ApiChapterPages>>()
            .props.pageProps.chapter.pages

        val pages = chapterPages.parseAs<List<String>>()

        val cdnUrl = updatedCdnUrl(document)

        return pages.mapIndexed { idx, image ->
            Page(idx, "", "$cdnUrl$image")
        }
    }

    private fun updatedCdnUrl(document: Document): String {
        val cdnUrlFromPage = document.selectFirst("main div.maxWidth img")
            ?.attr("src")
            ?.substringBefore("?")
            ?.let { "$it?fileId=" }

        return preferences.getCdnUrl()
            .let {
                if (it != cdnUrlFromPage && cdnUrlFromPage != null) {
                    preferences.putCdnUrl(cdnUrlFromPage)
                    cdnUrlFromPage
                } else {
                    it
                }
            }
    }

    private inline fun <reified T> String.parseAs(): T =
        json.decodeFromString(this)

    private inline fun <reified T> Response.parseAs(): T =
        body.string().parseAs()

    private fun SharedPreferences.getCdnUrl(): String {
        return getString(cdnPref, fallbackCdnUrl) ?: fallbackCdnUrl
    }

    private fun SharedPreferences.putCdnUrl(url: String) {
        edit().putString(cdnPref, url).commit()
    }

    companion object {
        private const val fallbackCdnUrl = "https://f005.backblazeb2.com/b2api/v1/b2_download_file_by_id?fileId="
        private const val cdnPref = "cdn_pref"
        val titleSpecialCharactersRegex = "[^a-z0-9]+".toRegex()
        val trailingHyphenRegex = "-+$".toRegex()
        val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        }
        const val PREFIX_SLUG = "slug:"
    }

    override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException("Not Used")

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("Not Used")

    override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException("Not Implemented")

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("Not Implemented")
}
