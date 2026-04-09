package eu.kanade.tachiyomi.multisrc.ezmanhwa

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

abstract class EZManhwa(
    override val name: String,
    override val baseUrl: String,
    val apiUrl: String,
    final override val lang: String = "en",
) : HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "application/json, text/plain, */*")
        .set("Referer", "$baseUrl/")

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.url}"

    // Chapter URLs are stored as: series/{seriesSlug}/chapters/{chapterSlug}
    // The website drops "chapters/" from the path, so we strip it here.
    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url.replace("/chapters/", "/")}"

    // ── Browse ───────────────────────────────────────────────────────────────

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/series?page=$page&perPage=20&sort=popular", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/series?page=$page&perPage=20&sort=latest", headers)

    override fun popularMangaParse(response: Response) = parseSeriesList(response)
    override fun latestUpdatesParse(response: Response) = parseSeriesList(response)

    private fun parseSeriesList(response: Response): MangasPage {
        val dto = response.parseAs<EZManhwaSeriesListDto>()
        val mangas = dto.data.mapNotNull { if (it.type != "NOVEL") it.toSManga() else null }
        return MangasPage(mangas, dto.currentPage < dto.totalPages)
    }

    // ── Search ───────────────────────────────────────────────────────────────

    // Base implementation sends filters only during browse.
    // Override if the source's search endpoint behaviour differs.
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val isSearch = query.isNotBlank()
        val endpoint = if (isSearch) "$apiUrl/series/search" else "$apiUrl/series"
        val url = endpoint.toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", "20")
            if (isSearch) {
                addQueryParameter("q", query)
            } else {
                var sortAdded = false
                for (filter in filters) {
                    when (filter) {
                        is EZManhwaSortFilter -> {
                            addQueryParameter("sort", filter.value)
                            sortAdded = true
                        }
                        is EZManhwaStatusFilter -> if (filter.value.isNotBlank()) addQueryParameter("status", filter.value)
                        is EZManhwaTypeFilter -> if (filter.value.isNotBlank()) addQueryParameter("type", filter.value)
                        else -> {}
                    }
                }
                if (!sortAdded) addQueryParameter("sort", "latest")
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseSeriesList(response)

    // ── Details ──────────────────────────────────────────────────────────────

    override fun mangaDetailsRequest(manga: SManga) = GET("$apiUrl/series/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response) = response.parseAs<EZManhwaSeriesDto>().toSManga().apply { initialized = true }

    // ── Chapters ─────────────────────────────────────────────────────────────

    override fun chapterListRequest(manga: SManga) = GET("$apiUrl/series/${manga.url}/chapters?page=1&perPage=100&sort=desc", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        // pathSegments for /api/v1/series/{slug}/chapters: [api, v1, series, slug, chapters]
        val seriesSlug = response.request.url.pathSegments[3]
        val initialData = response.parseAs<EZManhwaChapterListDto>()
        val chapters = mutableListOf<SChapter>()

        fun parsePage(dto: EZManhwaChapterListDto) {
            dto.data.mapNotNullTo(chapters) {
                if (shouldShowChapter(it)) it.toSChapter(seriesSlug) else null
            }
        }

        parsePage(initialData)
        var curr = initialData.currentPage
        while (curr < initialData.totalPages) {
            curr++
            val nextUrl = response.request.url.newBuilder()
                .setQueryParameter("page", curr.toString()).build()
            parsePage(
                client.newCall(GET(nextUrl, headers)).execute()
                    .parseAs<EZManhwaChapterListDto>(),
            )
        }
        return chapters
    }

    open fun shouldShowChapter(chapter: EZManhwaChapterDto): Boolean = chapter.requiresPurchase != true ||
        preferences.getBoolean(SHOW_LOCKED_CHAPTER_PREF_KEY, false)

    // ── Pages ────────────────────────────────────────────────────────────────

    override fun pageListRequest(chapter: SChapter) = GET("$apiUrl/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<EZManhwaPageListDto>()
        if (data.requiresPurchase == true) {
            throw Exception(
                "Chapter requires purchase (${data.totalImages} pages). " +
                    "Log in via webview and purchase to read.",
            )
        }
        return data.images?.mapIndexed { i, img -> Page(i, imageUrl = img.url) }
            ?: throw Exception("No images found. Chapter may be locked or require login via webview.")
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(EZManhwaSortFilter(), EZManhwaStatusFilter(), EZManhwaTypeFilter())

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
