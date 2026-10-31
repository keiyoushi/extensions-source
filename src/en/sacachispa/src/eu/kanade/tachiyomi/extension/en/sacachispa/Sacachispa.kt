package eu.kanade.tachiyomi.extension.en.sacachispa

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Sacachispa :
    KeiSource(),
    ConfigurableSource {

    override val imageFetch = Fetch.IMAGE

    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaList(page, "$API_URL/manga")

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = fetchMangaList(page, "$API_URL/manga/search", query)

    private suspend fun fetchMangaList(page: Int, endpoint: String, query: String? = null): MangasPage {
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply {
                if (query != null) addQueryParameter("q", query)
            }
            .build()

        val response = client.get(url, Fetch.XHR).parseAs<MangaListResponse>()

        return MangasPage(
            mangas = response.data.map { it.toSManga() },
            hasNextPage = response.pagination.page < response.pagination.pages,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "manga") return null

        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val manga = SManga.create().apply { this.url = id }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchMangaDetails(manga) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(manga) } else null

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga = client.get("$API_URL/manga/${manga.url}", Fetch.XHR)
        .parseAs<MangaResponse>().data
        .toSManga(manga.url)

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        // Entries from before the site rewrite store the slug, the releases endpoint takes the id.
        val mangaId = manga.url.takeIf { UUID_REGEX.matches(it) }
            ?: manga.memo.getStringOrNull("id")
            ?: client.get("$API_URL/manga/${manga.url}", Fetch.XHR).parseAs<MangaResponse>().data.id

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_CHAPTERS, true)
        val chapters = mutableListOf<SChapter>()
        var page = 1

        do {
            val url = "$API_URL/releases".toHttpUrl().newBuilder()
                .addQueryParameter("mangaId", mangaId)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", CHAPTER_PAGE_SIZE.toString())
                .build()
            val response = client.get(url, Fetch.XHR).parseAs<ReleaseListResponse>()

            chapters += response.data.mapNotNull { it.toSChapter(hideLocked) }
        } while (page++ < response.pagination.pages)

        return chapters.sortedByDescending { it.chapter_number }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$API_URL/releases/${chapter.url}/pages", Fetch.XHR, ensureSuccess = false)

        // Patreon-exclusive chapters answer 403 with a message for the reader.
        if (response.code == 403 && response.header("Content-Type").orEmpty().startsWith("application/json")) {
            throw Exception(response.parseAs<ErrorResponse>().error.message)
        }
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }

        return response.parseAs<PageListResponse>().data.items
            .mapIndexed { index, item -> Page(index, imageUrl = item.url) }
    }

    // The slug segment is cosmetic, so the id stands in for it until details have loaded.
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}/${manga.memo.getStringOrNull("slug") ?: manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/${chapter.url}"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_CHAPTERS
            title = "Hide Patreon-exclusive chapters"
            summaryOn = "Chapters will be hidden"
            summaryOff = "Chapters will be marked with an icon: 🔒"
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    private companion object {
        const val API_URL = "https://api.sacachispa.site/api"
        const val HIDE_LOCKED_CHAPTERS = "hide_patreon_chapters"
        const val PAGE_SIZE = 24
        const val CHAPTER_PAGE_SIZE = 500
        val UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
