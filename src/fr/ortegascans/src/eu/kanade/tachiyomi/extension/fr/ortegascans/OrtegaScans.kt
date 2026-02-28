package eu.kanade.tachiyomi.extension.fr.ortegascans

import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class OrtegaScans :
    HttpSource(),
    ConfigurableSource {
    override val name = "Ortega Scans"
    override val lang = "fr"

    override val baseUrl = "https://ortegascans.fr"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val rscHeaders = headersBuilder()
        .add("rsc", "1")
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val filters = getFilterList().apply {
            firstInstance<SortFilter>().state = 0
        }
        return searchMangaRequest(page, "", filters)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val filters = getFilterList().apply {
            firstInstance<SortFilter>().state = 2
        }
        return searchMangaRequest(page, "", filters)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortFilter = filters.firstInstance<SortFilter>()
        val statusFilter = filters.firstInstance<StatusFilter>()
        val tagFilter = filters.firstInstance<TagFilter>()
        val minChaptersFilter = filters.firstInstance<MinChaptersFilter>()
        val maxChaptersFilter = filters.firstInstance<MaxChaptersFilter>()

        val url = "$baseUrl/api/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", "18")
            addQueryParameter("page", page.toString())
            addQueryParameter("search", query)
            addQueryParameter("tags", tagFilter.values)
            addQueryParameter("status", statusFilter.values)
            addQueryParameter("sort", sortFilter.value)
            addQueryParameter("minChapters", minChaptersFilter.value)
            addQueryParameter("isOrtegaOnly", "false")
            addQueryParameter("unreadOnly", "false")
            addQueryParameter("maxChapters", maxChaptersFilter.value)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SeriesResponse>()
        val mangas = dto.data.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, dto.hasMore)
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        TagFilter(),
        MinChaptersFilter(),
        MaxChaptersFilter(),
    )

    override fun mangaDetailsRequest(manga: SManga): Request {
        val urlObj = manga.url.parseAs<MangaUrl>()
        return GET("$baseUrl/serie/${urlObj.slug}", rscHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.extractNextJs<MangaDetailsDataDto>()
            ?: throw Exception("Impossible d'extraire les détails du manga")
        return dto.manga.toSManga(baseUrl)
    }

    override fun getMangaUrl(manga: SManga): String {
        val urlObj = manga.url.parseAs<MangaUrl>()
        return "$baseUrl/serie/${urlObj.slug}"
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.extractNextJs<ChapterListDataDto>()
            ?: throw Exception("Impossible d'extraire la liste des chapitres")
        val slug = response.request.url.pathSegments[1]
        val hidePremium = preferences.getBoolean(PREF_HIDE_PREMIUM, false)

        return dto.chapters
            .filter { !hidePremium || !it.isPremium }
            .map { it.toSChapter(slug) }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val urlObj = chapter.url.parseAs<ChapterUrl>()
        return GET("$baseUrl/serie/${urlObj.mangaSlug}/chapter/${urlObj.number}", rscHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.extractNextJs<PageListDto>()
            ?: throw Exception("Impossible d'extraire la liste des pages")
        return dto.images.map {
            val url = if (it.url.startsWith("http")) {
                it.url
            } else {
                "$baseUrl${it.url}"
            }
            Page(it.index, imageUrl = url)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val urlObj = chapter.url.parseAs<ChapterUrl>()
        return "$baseUrl/serie/${urlObj.mangaSlug}/chapter/${urlObj.number}"
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_PREMIUM
            title = "Masquer les chapitres premium"
            summary = "Masquer les chapitres verrouillés du site"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }
}

private const val PREF_HIDE_PREMIUM = "pref_hide_premium"
