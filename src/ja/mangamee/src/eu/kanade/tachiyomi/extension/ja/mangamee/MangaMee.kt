package eu.kanade.tachiyomi.extension.ja.mangamee

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
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAsProto
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaMee :
    KeiSource(),
    ConfigurableSource {
    private val domain = "manga-mee.jp"
    private val apiUrl = "https://prod2-android.$domain/web/v1"
    private val preferences by getPreferencesLazy()
    private val rscHeaders get() = headersBuilder()
        .set("rsc", "1")
        .build()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$baseUrl/title-list/ranking", rscHeaders).extractNextJs<RankingResponse>()
        val mangas = result?.all?.rankingList.orEmpty()
            .find { it.name == "総合" }?.titles.orEmpty()
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = client.get("$baseUrl/title-list/todaysupdate", rscHeaders).extractNextJs<LatestResponse>()
        val mangas = result?.titleGroup?.titles.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search-result/keyword".toHttpUrl().newBuilder()
            .addPathSegment(query)
            .build()

        val result = client.get(url, rscHeaders).extractNextJs<SearchResponse>()
        val mangas = result?.popularTitles?.titles.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            val url = "$apiUrl/title_detail".toHttpUrl().newBuilder()
                .addQueryParameter("title_id", manga.url)
                .build()
            client.get(url).parseAsProto<DetailResponse>().toSManga()
        }
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
            client.get("$baseUrl/all-episodes/${manga.url}", rscHeaders)
                .extractNextJs<ChapterResponse>()?.allEpisodes?.episodes.orEmpty()
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter(manga.url) }
                .reversed()
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/title_detail".toHttpUrl().newBuilder()
            .addQueryParameter("title_id", chapter.memo["titleId"]!!.string)
            .addQueryParameter("episode_id", chapter.url)
            .build()

        val result = client.get(url).parseAsProto<DetailResponse>()
        if (result.pages.isNullOrEmpty()) {
            throw Exception("This chapter is only accessible via the official マンガMee app.")
        }
        return result.pages.mapNotNull { it.mainPage }.mapIndexed { i, pages ->
            Page(i, imageUrl = "${pages.imageUrl}#key=${pages.key}")
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/detail/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/detail/${chapter.memo["titleId"]!!.string}?episodeId=${chapter.url}"

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
}
