package eu.kanade.tachiyomi.extension.ja.ganma

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
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.boolean
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.persistedQueryExtension
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate
import java.time.ZoneId

@Source
abstract class Ganma :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "$baseUrl/api/graphql"
    private val preferences by getPreferencesLazy()
    private val jst = ZoneId.of("Asia/Tokyo")

    // Custom UA needed to read chapters.
    private val readerHeaders get() = headersBuilder()
        .set("User-Agent", "GanmaReader/10.11.0 Android")
        .build()

    // Desktop UA needed to read web only chapters.
    private val desktopHeaders get() = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36")
        .build()

    private var lastCursor: String? = null

    override fun getHomeUrl(): String = "$baseUrl/web"

    override fun Headers.Builder.configureHeaders() = apply {
        set("X-From", "$baseUrl/web")
    }

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.post(
            apiUrl,
            desktopHeaders,
            graphQLBody(
                operationName = "home",
                variables = EmptyVariables,
                extensions = persistedQueryExtension(HASH_HOME),
            ),
        ).parseGraphQLAs<HomeDto>()
        val mangas = result.ranking.totalRanking.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page == 1) lastCursor = null
        return dayOfWeekPage(LocalDate.now(jst).dayOfWeek.name)
    }

    private suspend fun dayOfWeekPage(dayOfWeek: String): MangasPage {
        val result = client.post(
            apiUrl,
            desktopHeaders,
            graphQLBody(
                operationName = "serialMagazinesByDayOfWeek",
                variables = DayOfWeekVariables(dayOfWeek, lastCursor),
                extensions = persistedQueryExtension(HASH_SERIAL_MAGAZINES_BY_DAY_OF_WEEK),
            ),
        ).parseGraphQLAs<LatestResponse>().serialPerDayOfWeek.panels
        val mangas = result.edges.map { it.node.storyInfo.magazine.toSManga() }
        lastCursor = result.pageInfo.endCursor
        return MangasPage(mangas, result.pageInfo.hasNextPage)
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page == 1) lastCursor = null
        if (query.isNotBlank()) {
            val result = client.post(
                apiUrl,
                desktopHeaders,
                graphQLBody(
                    operationName = "magazinesByKeywordSearch",
                    variables = SearchVariables(query, lastCursor),
                    extensions = persistedQueryExtension(HASH_MAGAZINES_BY_KEYWORD_SEARCH),
                ),
            ).parseGraphQLAs<SearchResponse>().searchComic
            val mangas = result.edges.map { it.node.toSManga() }
            lastCursor = result.pageInfo.endCursor
            return MangasPage(mangas, result.pageInfo.hasNextPage)
        }

        val category = filters.firstInstance<CategoryFilter>().value
        if (category != "finished") {
            return dayOfWeekPage(category)
        }

        val result = client.post(
            apiUrl,
            desktopHeaders,
            graphQLBody(
                operationName = "finishedMagazines",
                variables = FinishedVariables(lastCursor),
                extensions = persistedQueryExtension(HASH_FINISHED_MAGAZINES),
            ),
        ).parseGraphQLAs<FinishedResponseDto>().magazinesByCategory.magazines
        val mangas = result.edges.map { it.node.toSManga() }
        lastCursor = result.pageInfo.endCursor
        return MangasPage(mangas, result.pageInfo.hasNextPage)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(),
    )

    override fun getMangaUrl(manga: SManga) = "$baseUrl/web/magazine/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val details = async {
            if (!fetchDetails) return@async manga
            client.post(
                apiUrl,
                desktopHeaders,
                graphQLBody(
                    operationName = "magazineDetail",
                    variables = MagazineDetailVariables(manga.url),
                    extensions = persistedQueryExtension(HASH_MAGAZINE_DETAIL),
                ),
            ).parseGraphQLAs<DetailsResponse>().magazine.toSManga()
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters
            client.post(
                apiUrl,
                desktopHeaders,
                graphQLBody(
                    operationName = "storyInfoList",
                    variables = ChapterListVariables(manga.url, 9999, null),
                    extensions = persistedQueryExtension(HASH_STORY_INFO_LIST),
                ),
            ).parseGraphQLAs<ChapterResponse>().magazine.storyInfos.edges
                .filter { !hideLocked || !it.node.isLocked }
                .map { it.node.toSChapter(manga.url) }
                .reversed()
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/web/reader/${chapter.memo["alias"]!!.string}/${chapter.url}/0"

    // Viewer
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val apiHeaders = if (chapter.memo["desktop"]!!.boolean) desktopHeaders else readerHeaders
        val result = client.post(
            apiUrl,
            apiHeaders,
            graphQLBody(
                operationName = "magazineStoryForReader",
                variables = ViewerVariables(chapter.memo["alias"]!!.string, chapter.url),
                extensions = persistedQueryExtension(HASH_MAGAZINE_STORY_FOR_READER),
            ),
        ).parseGraphQLAs<ViewerResponse>().magazine.storyContents

        if (result.error != null) {
            throw Exception("Log in via WebView and get premium or purchase this chapter to read.")
        }

        val pageImages = result.pageImages!!
        return buildList {
            (1..pageImages.pageCount).mapTo(this) {
                val url = "${pageImages.pageImageBaseUrl}$it.jpg".toHttpUrl().newBuilder()
                    .encodedQuery(pageImages.pageImageSign)
                    .setQueryParameter("w", "4999")
                    .build()
                    .toString()
                Page(it - 1, imageUrl = url)
            }
            result.afterword?.imageUrl?.let {
                val url = it.toHttpUrl().newBuilder()
                    .setQueryParameter("w", "4999")
                    .build()
                    .toString()
                add(Page(size, imageUrl = url))
            }
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
        // https://web.archive.org/web/20260309082826/https://ganma.jp/web/_next/static/chunks/app/layout-fbbe5dd886d24cb7.js
        private const val HASH_HOME = "b65659a4a5689bac97168591122219b69ee089d840b0415ace241d0caebee900"
        private const val HASH_MAGAZINE_DETAIL = "9a1460a42f8d04c70b23bb9ad763d0dbef2eb6f5d05dafca98ca2be8a2bfe867"
        private const val HASH_STORY_INFO_LIST = "acd460c52a231029d09e1ccca0aa06b99ae8163d5edff661cd64984ebb6dc4c3"
        private const val HASH_MAGAZINE_STORY_FOR_READER = "44e35d8af09515a315b06090723b72753828cf799466e3e1d722786844676617"
        private const val HASH_MAGAZINES_BY_KEYWORD_SEARCH = "55c7ca6cce30d8abdb0b32d00ad678ba37c03dd9b4851daf5ab5df5d41ce3ccc"
        private const val HASH_FINISHED_MAGAZINES = "ade49c46df5ef36f15485df70f656fb14f3261e90863fcd9ffbcc10baf30bc4c"
        private const val HASH_SERIAL_MAGAZINES_BY_DAY_OF_WEEK = "f1778757c51a4f8b59d91032096dd11b2071cb4191ca1904744672c814d16a97"
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
