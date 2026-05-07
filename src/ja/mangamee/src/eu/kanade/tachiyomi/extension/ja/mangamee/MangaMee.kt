package eu.kanade.tachiyomi.extension.ja.mangamee

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAsProto
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class MangaMee :
    HttpSource(),
    ConfigurableSource {
    override val name = "MangaMee"
    private val domain = "manga-mee.jp"
    override val baseUrl = "https://manga-mee.jp"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl = "https://prod2-android.$domain/web/v1"
    private val preferences by getPreferencesLazy()
    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/title-list/ranking", rscHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.extractNextJs<RankingResponse>()
        val mangas = result?.all?.rankingList.orEmpty().find { it.name == "総合" }?.titles.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/title-list/todaysupdate", rscHeaders)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.extractNextJs<LatestResponse>()
        val mangas = result?.titleGroup?.titles.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search-result/keyword".toHttpUrl().newBuilder()
            .addPathSegment(query)
            .build()
        return GET(url, rscHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.extractNextJs<SearchResponse>()
        val mangas = result?.popularTitles?.titles.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/title_detail".toHttpUrl().newBuilder()
            .addQueryParameter("title_id", manga.url)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAsProto<DetailResponse>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/detail/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/all-episodes/${manga.url}", rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val titleId = response.request.url.pathSegments.last()
        return response.extractNextJs<ChapterResponse>()?.allEpisodes?.episodes.orEmpty()
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter(titleId) }
            .reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterUrl = "$baseUrl/${chapter.url}".toHttpUrl()
        val titleId = chapterUrl.fragment
        val chapterId = chapterUrl.pathSegments.first()
        return "$baseUrl/detail/$titleId?episodeId=$chapterId"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = "$baseUrl/${chapter.url}".toHttpUrl()
        val titleId = chapterUrl.fragment
        val chapterId = chapterUrl.pathSegments.first()
        val url = "$apiUrl/title_detail".toHttpUrl().newBuilder()
            .addQueryParameter("title_id", titleId)
            .addQueryParameter("episode_id", chapterId)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAsProto<DetailResponse>()
        if (result.pages.isNullOrEmpty()) {
            throw Exception("This chapter is only accessible via the official マンガMee app.")
        }
        return result.pages.mapNotNull { it.mainPage }.mapIndexed { i, pages ->
            Page(i, imageUrl = "${pages.imageUrl}#key=${pages.key}")
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
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
