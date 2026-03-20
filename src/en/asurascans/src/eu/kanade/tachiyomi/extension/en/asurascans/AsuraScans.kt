package eu.kanade.tachiyomi.extension.en.asurascans

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
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import kotlin.text.replace

class AsuraScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Asura Scans"

    override val baseUrl = "https://asurascans.com"

    private val apiUrl = "https://api.asurascans.com/api"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences: SharedPreferences = getPreferences()

    init {
        // remove legacy preferences
        preferences.run {
            if (contains("pref_url_map")) {
                edit().remove("pref_url_map").apply()
            }
            if (contains("pref_base_url_host")) {
                edit().remove("pref_base_url_host").apply()
            }
            if (contains("pref_permanent_manga_url_2_en")) {
                edit().remove("pref_permanent_manga_url_2_en").apply()
            }
            if (contains("pref_slug_map")) {
                edit().remove("pref_slug_map").apply()
            }
            if (contains("pref_dynamic_url")) {
                edit().remove("pref_dynamic_url").apply()
            }
            if (contains("pref_slug_map_2")) {
                edit().remove("pref_slug_map_2").apply()
            }
            if (contains("pref_force_high_quality")) {
                edit().remove("pref_force_high_quality").apply()
            }
        }
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2, 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter(defaultSort = "popular")))

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter(defaultSort = "latest")))

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()

        url.addQueryParameter("offset", ((page - 1) * PER_PAGE_LIMIT).toString())
        url.addQueryParameter("limit", PER_PAGE_LIMIT.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        filters.filterIsInstance<UriFilter>().forEach {
            it.addToUri(url)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<DataDto<List<MangaDto>>>()
        val mangas = result.data.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, result.meta!!.hasMore)
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenresFilter(),
        MinChaptersFilter(),
    )

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}".replace("/series/", "/comics/")

    override fun mangaDetailsRequest(manga: SManga): Request {
        val match = OLD_FORMAT_MANGA_REGEX.find(manga.url)?.groupValues?.get(2)
        val slug = match ?: manga.url.substringAfter("/series/").substringBefore("/")
        return GET("$apiUrl/series/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val json = response.body.string()
        val mangaData = run {
            val wrapper = json.parseAs<DataDto<MangaDetailsDto>>()
            wrapper.data ?: json.parseAs<MangaDetailsDto>()
        }

        return mangaData.series.toSMangaDetails()
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}".replace("/series/", "/comics/")

    override fun chapterListRequest(manga: SManga): Request {
        val match = OLD_FORMAT_MANGA_REGEX.find(manga.url)?.groupValues?.get(2)
        val slug = match ?: manga.url.substringAfter("/series/").substringBefore("/")
        return GET("$apiUrl/series/$slug/chapters?offset=0&limit=500", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chaptersData = response.parseAs<DataDto<List<ChapterDto>>>()
        val hidePremium = preferences.hidePremiumChapters()

        return chaptersData.data.orEmpty()
            .filterNot { hidePremium && it.isLocked }
            .map { it.toSChapter() }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaSlug = chapter.url.substringAfter("/series/").substringBefore("/")
        val number = chapter.url.substringAfter("/chapter/")
        return GET("$apiUrl/series/$mangaSlug/chapters/$number", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterData = response.parseAs<DataDto<ChapterWrapperDto>>().data
        return chapterData!!.chapter.pages.orEmpty().mapIndexed { index, pageDto ->
            Page(index, imageUrl = pageDto.url)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_PREMIUM_CHAPTERS
            title = "Hide premium chapters"
            summary = "Hides the chapters that require a subscription to view"
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    private fun SharedPreferences.hidePremiumChapters(): Boolean = getBoolean(
        PREF_HIDE_PREMIUM_CHAPTERS,
        true,
    )

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private const val PER_PAGE_LIMIT = 20
        private val OLD_FORMAT_MANGA_REGEX = """^/manga/(\d+-)?([^/]+)/?$""".toRegex()
        private const val PREF_HIDE_PREMIUM_CHAPTERS = "pref_hide_premium_chapters"
    }
}
