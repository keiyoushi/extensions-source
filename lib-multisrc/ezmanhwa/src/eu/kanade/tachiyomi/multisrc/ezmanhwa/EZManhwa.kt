package eu.kanade.tachiyomi.multisrc.ezmanhwa

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

abstract class EZManhwa :
    KeiSource(),
    ConfigurableSource {

    abstract val apiUrl: String

    private val preferences by getPreferencesLazy()

    override fun Headers.Builder.configureHeaders(): Headers.Builder = addEZManhwaHeaders()

    protected fun Headers.Builder.addEZManhwaHeaders(): Headers.Builder = set("Accept", "application/json, text/plain, */*")

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.url}"

    // Chapter URLs are stored as: series/{seriesSlug}/chapters/{chapterSlug}
    // The website drops "chapters/" from the path, so we strip it here.
    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url.replace("/chapters/", "/")}"

    // ── Browse ───────────────────────────────────────────────────────────────

    override suspend fun getPopularManga(page: Int) = parseSeriesList(client.get("$apiUrl/series?page=$page&perPage=20&sort=popular"))

    override suspend fun getLatestUpdates(page: Int) = parseSeriesList(client.get("$apiUrl/series?page=$page&perPage=20&sort=latest"))

    private fun parseSeriesList(response: Response): MangasPage {
        val dto = response.parseAs<EZManhwaSeriesListDto>()
        val mangas = dto.data.mapNotNull { if (it.type != "NOVEL") it.toSManga() else null }
        return MangasPage(mangas, dto.currentPage < dto.totalPages)
    }

    // ── Search ───────────────────────────────────────────────────────────────

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = parseSeriesList(client.get(searchMangaUrl(page, query, filters)))

    // Base implementation sends filters only during browse.
    // Override if the source's search endpoint behaviour differs.
    protected open fun searchMangaUrl(page: Int, query: String, filters: FilterList): HttpUrl {
        val isSearch = query.isNotBlank()
        val endpoint = if (isSearch) "$apiUrl/series/search" else "$apiUrl/series"
        return endpoint.toHttpUrl().newBuilder().apply {
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
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        return client.get("$apiUrl/series/$slug").parseAs<EZManhwaSeriesDto>().toSManga()
    }

    // ── Details ──────────────────────────────────────────────────────────────

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) client.get("$apiUrl/series/${manga.url}").parseAs<EZManhwaSeriesDto>().toSManga() else manga
        }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga.url) else chapters }

        SMangaUpdate(details.await(), chapterList.await())
    }

    // ── Chapters ─────────────────────────────────────────────────────────────

    private suspend fun fetchChapterList(seriesSlug: String): List<SChapter> {
        val url = "$apiUrl/series/$seriesSlug/chapters?page=1&perPage=100&sort=desc".toHttpUrl()
        val initialData = client.get(url).parseAs<EZManhwaChapterListDto>()
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
            val nextUrl = url.newBuilder()
                .setQueryParameter("page", curr.toString()).build()
            parsePage(client.get(nextUrl).parseAs<EZManhwaChapterListDto>())
        }
        return chapters
    }

    open fun shouldShowChapter(chapter: EZManhwaChapterDto): Boolean = chapter.requiresPurchase != true ||
        preferences.getBoolean(SHOW_LOCKED_CHAPTER_PREF_KEY, false)

    // ── Pages ────────────────────────────────────────────────────────────────

    protected open fun pageListUrl(chapter: SChapter) = "$apiUrl/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(pageListUrl(chapter)).parseAs<EZManhwaPageListDto>()
        if (data.requiresPurchase == true) {
            throw Exception(
                "Chapter requires purchase (${data.totalImages} pages). " +
                    "Log in via webview and purchase to read.",
            )
        }
        return data.images?.mapIndexed { i, img -> Page(i, imageUrl = img.url) }
            ?: throw Exception("No images found. Chapter may be locked or require login via webview.")
    }

    override fun getFilterList(data: JsonElement?) = FilterList(EZManhwaSortFilter(), EZManhwaStatusFilter(), EZManhwaTypeFilter())

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
