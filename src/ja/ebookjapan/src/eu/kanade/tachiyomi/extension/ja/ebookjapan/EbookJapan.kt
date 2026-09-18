package eu.kanade.tachiyomi.extension.ja.ebookjapan

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

@Source
abstract class EbookJapan :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "$baseUrl/proxy/apis"
    private val cdnUrl get() = "https://prod-contents-br-page.akamaized.net/pages"
    private val viewerUrl get() = "$baseUrl/br_api"
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addCookie("ebaf" to "1")
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if ((response.code == 400 || response.code == 404) && request.url.pathSegments.last() == "open_book") {
                response.close()
                throw IOException("Log in via WebView and purchase this product to read.")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val start = (page - 1) * PER_PAGE
        val url = "$apiUrl/ranking/unificationTitles".toHttpUrl().newBuilder()
            .addQueryParameter("type", "charge")
            .addQueryParameter("term", "recent")
            .addQueryParameter("start", start.toString())
            .addQueryParameter("results", PER_PAGE.toString())
            .addQueryParameter("isRatingHigh", "1")
            .build()

        val result = client.get(url).parseAs<RankingResponse>().rankingTitles
        val mangas = result.unificationTitles.map { it.toSManga() }
        return MangasPage(mangas, start + PER_PAGE < result.totalResults)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val start = (page - 1) * PER_PAGE
        val url = "$apiUrl/recent/details".toHttpUrl().newBuilder()
            .addQueryParameter("useTitle", "0")
            .addQueryParameter("start", start.toString())
            .addQueryParameter("results", PER_PAGE.toString())
            .build()

        val result = client.get(url).parseAs<LatestResponse>()
        val mangas = result.items.map { it.toSManga() }
        return MangasPage(mangas, start + PER_PAGE < result.totalResults)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val start = (page - 1) * PER_PAGE
        val url = "$apiUrl/search/titles".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("start", start.toString())
            .addQueryParameter("results", PER_PAGE.toString())
            .addQueryParameter("sort", "weeklyPurchasedRanking")
            .build()

        val result = client.get(url).parseAs<SearchResponse>()
        val mangas = result.items.map { it.toSManga() }
        return MangasPage(mangas, start + PER_PAGE < result.totalResults)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/books/${manga.url}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detail = async {
            val url = "$apiUrl/books/titleV2/sync".toHttpUrl().newBuilder()
                .addQueryParameter("titleId", manga.url)
                .build()
            client.get(url).parseAs<DetailResponse>()
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val volumes = async { getVolumes(manga.url, hideLocked) }
            val stories = detail.await().serialStory?.let { async { getStories(it.serialStoryId, hideLocked) } }
            stories?.await().orEmpty() + volumes.await()
        }

        SMangaUpdate(
            detail.await().toSManga(),
            chapterList.await(),
        )
    }

    private suspend fun getStories(serialStoryId: String, hideLocked: Boolean): List<SChapter> {
        val url = "$apiUrl/books/titleV2/storyList".toHttpUrl().newBuilder()
            .addQueryParameter("serialStoryId", serialStoryId)
            .addQueryParameter("start", "0")
            .addQueryParameter("results", "9999")
            .addQueryParameter("sort", "asc")
            .addQueryParameter("isSortAsc", "0")
            .build()

        return client.get(url).parseAs<StoryListResponse>().stories
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter() }
    }

    private suspend fun getVolumes(titleId: String, hideLocked: Boolean): List<SChapter> {
        val url = "$apiUrl/books/titleV2/publicationList".toHttpUrl().newBuilder()
            .addQueryParameter("titleId", titleId)
            .build()

        return client.get(url).parseAs<PublicationListResponse>().publications.orEmpty()
            .filter { !hideLocked || !it.isLocked }
            .reversed()
            .map { it.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val readType = chapter.memo.getStringOrNull(MEMO_TYPE) ?: TYPE_FREE
        val viewer = "$baseUrl/viewer/$readType/${chapter.url}/"
        val serialStoryId = chapter.memo.getStringOrNull(MEMO_SSID) ?: return viewer
        return "$viewer?ssid=$serialStoryId"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = OpenBookRequest(
            type = chapter.memo.getStringOrNull(MEMO_TYPE) ?: TYPE_FREE,
            code = chapter.url,
            ssid = chapter.memo.getStringOrNull(MEMO_SSID),
            light = false,
        ).toJsonRequestBody()

        val session = client.post("$viewerUrl/open_book", body).parseAs<OpenBookResponse>()
        val drmUrl = "$viewerUrl/get_drm".toHttpUrl().newBuilder()
            .addQueryParameter("session_id", session.sessionId)
            .build()

        val drm = client.get(drmUrl).parseAs<DrmResponse>()
        if (!drm.isFixedLayout) {
            throw Exception("Novels and other reflowable books are not supported.")
        }

        val book = decryptBook(session.sessionId, drm.code, session.payload, drm.payload, drm.fileId)

        return List(book.pageCount) { index ->
            Page(index, imageUrl = "$cdnUrl/${book.pageName(index)}#${book.scramble(index).encode()}")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PER_PAGE = 50
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
