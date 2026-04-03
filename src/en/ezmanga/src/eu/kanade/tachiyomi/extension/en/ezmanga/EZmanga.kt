package eu.kanade.tachiyomi.extension.en.ezmanga

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EZmanga :
    HttpSource(),
    ConfigurableSource {

    override val name = "EZmanga"
    override val baseUrl = "https://ezmanga.org"
    override val lang = "en"
    override val supportsLatest = true

    private val v1Api = "https://vapi.ezmanga.org/api/v1"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "application/json, text/plain, */*")
        .set("Referer", "$baseUrl/")

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // -- Search & Browse --

    override fun popularMangaRequest(page: Int): Request = GET("$v1Api/series?page=$page&perPage=20&sort=popular", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$v1Api/series?page=$page&perPage=20&sort=latest", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val isSearch = query.isNotBlank()
        val endpoint = if (isSearch) "$v1Api/series/search" else "$v1Api/series"

        val url = endpoint.toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", "20")
            if (isSearch) {
                // Search endpoint ignores all filters — only send the query
                addQueryParameter("q", query)
            } else {
                var sortAdded = false
                for (filter in filters) {
                    when (filter) {
                        is SortFilter -> {
                            addQueryParameter("sort", filter.value)
                            sortAdded = true
                        }
                        is StatusFilter -> if (filter.value.isNotBlank()) addQueryParameter("status", filter.value)
                        is TypeFilter -> if (filter.value.isNotBlank()) addQueryParameter("type", filter.value)
                        is GenreFilter -> if (filter.value.isNotBlank()) addQueryParameter("genre", filter.value)
                        else -> {}
                    }
                }
                if (!sortAdded) addQueryParameter("sort", "latest")
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SeriesListDto>()
        val mangas = dto.data.mapNotNull { if (it.type != "NOVEL") it.toSManga() else null }
        return MangasPage(mangas, dto.current < dto.totalPages)
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // -- Details & Chapters --

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$v1Api/series/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        // Set initialized = true only when full details are successfully parsed
        return response.parseAs<SeriesApiDto>().toSManga().apply {
            initialized = true
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$v1Api/series/${manga.url}/chapters?page=1&perPage=100&sort=desc", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val seriesSlug = response.request.url.pathSegments[3]
        val initialData = response.parseAs<ChapterListApiDto>()
        val showLocked = preferences.getBoolean(SHOW_LOCKED_CHAPTER_PREF_KEY, false)
        val chapters = mutableListOf<SChapter>()

        fun parsePage(dto: ChapterListApiDto) {
            dto.data.mapNotNullTo(chapters) {
                if (showLocked || it.requiresPurchase != true) it.toSChapter(seriesSlug) else null
            }
        }

        parsePage(initialData)
        var curr = initialData.current
        while (curr < initialData.totalPages) {
            curr++
            val nextUrl = response.request.url.newBuilder().setQueryParameter("page", curr.toString()).build()
            val nextCall = client.newCall(GET(nextUrl, headers)).execute()
            parsePage(nextCall.parseAs<ChapterListApiDto>())
        }
        return chapters
    }

    // -- Pages --

    override fun pageListRequest(chapter: SChapter): Request {
        val path = chapter.url.removePrefix("/series/")
        val slash = path.indexOf('/')
        return GET("$v1Api/series/${path.substring(0, slash)}/chapters/${path.substring(slash + 1)}", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PageListApiDto>()
        if (data.requiresPurchase == true) {
            throw Exception("Chapter requires purchase (${data.totalImages} pages). Log in via webview and purchase to read.")
        }
        return data.images?.mapIndexed { i, img -> Page(i, imageUrl = img.url) }
            ?: throw Exception("No images found. Chapter may be locked or require login via webview.")
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used")

    override fun getFilterList(): FilterList = FilterList(SortFilter(), StatusFilter(), TypeFilter(), GenreFilter())

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_CHAPTER_PREF_KEY
            title = "Show locked chapters"
            summary = "Show chapters requiring coins. Note: They only load if owned/logged in via webview."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        const val SHOW_LOCKED_CHAPTER_PREF_KEY = "pref_show_locked_chapters"
    }
}
