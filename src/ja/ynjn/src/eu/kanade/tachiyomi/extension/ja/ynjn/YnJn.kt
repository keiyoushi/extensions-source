package eu.kanade.tachiyomi.extension.ja.ynjn

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.time.ZoneId

@Source
abstract class YnJn :
    KeiSource(),
    ConfigurableSource {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://webapi.$domain"
    private val preferences by getPreferencesLazy()
    private val currentDateJst: LocalDate
        get() = LocalDate.now(ZoneId.of("Asia/Tokyo"))

    private var latestFeature: Pair<LocalDate, Int>? = null

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/title/ranking".toHttpUrl().newBuilder()
            .addQueryParameter("id", "1742")
            .addQueryParameter("type", "LIST")
            .addQueryParameter("rankingType", "RANKING")
            .build()

        val result = client.get(url).parseAs<RankingResponse>()
        val mangas = result.data.ranking.titles.map { it.title.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val today = currentDateJst
        val featureId = latestFeature?.takeIf { it.first == today }?.second
            ?: client.get("$apiUrl/title/feature?displayLocation=TOP_PAGE_RENSAI").parseAs<DataResponse>()
                .data.info!!.id.also { latestFeature = today to it }

        val url = "$apiUrl/title/feature".toHttpUrl().newBuilder()
            .addQueryParameter("id", featureId.toString())
            .addQueryParameter("page", page.toString())
            .build()

        val result = client.get(url).parseAs<DataResponse>().data
        val mangas = result.titles.map { it.toSManga() }
        val hasNextPage = result.hasNext == true
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstance<CategoryFilter>()
        val url = "$apiUrl/title/category".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("TEXT")
                addQueryParameter("text", query)
            } else {
                addPathSegment(category.type)
                addQueryParameter("id", category.value)
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("sort", "POPULARITY")
        }.build()

        val result = client.get(url).parseAs<DataResponse>().data
        val mangas = result.titles.map { it.toSManga() }
        val hasNextPage = (page - 1) * 12 + mangas.size < result.totalCount
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val details = async {
            if (!fetchDetails) return@async manga
            client.get("$apiUrl/book/${manga.url}").parseAs<TitleDetails>().data.book.toSManga()
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val url = "$apiUrl/title/${manga.url}/episode".toHttpUrl().newBuilder()
                .addQueryParameter("isGetAll", "true")
                .build()

            client.get(url).parseAs<ChapterDetails>().data.episodes
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter(manga.url) }
                .reversed()
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/viewer/${chapter.memo["titleId"]!!.string}/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("titleId", chapter.memo["titleId"]!!.string)
            .addQueryParameter("episodeId", chapter.url)
            .build()

        val pages = client.get(url).parseAs<ViewerDetails>().data.pages
        if (pages.isEmpty()) {
            throw Exception("Log in via WebView and purchase this chapter to read.")
        }

        return pages.mapNotNull { it.mangaPage }.map {
            Page(it.pageNumber, imageUrl = "${it.pageImageUrl}#scramble")
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(),
    )

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
