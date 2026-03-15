package eu.kanade.tachiyomi.extension.ja.ganma

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.Calendar
import java.util.TimeZone

class Ganma :
    HttpSource(),
    ConfigurableSource {
    override val name = "GANMA!"
    override val lang = "ja"
    override val baseUrl = "https://ganma.jp"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api/graphql"
    private val jst = TimeZone.getTimeZone("Asia/Tokyo")
    private val preferences: SharedPreferences by getPreferencesLazy()

    private var lastCursor: String? = null
    private val webOnlyAliases = mutableSetOf<String>()

    override fun headersBuilder() = super.headersBuilder()
        .add("X-From", "$baseUrl/web")

    // Popular
    override fun popularMangaRequest(page: Int) = graphQLRequest("home", HASH_HOME, EmptyVariables)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<GraphQLResponse<HomeDto>>()
        val mangas = result.data.ranking.totalRanking.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) lastCursor = null
        val today = getLatestDay()
        return graphQLRequest("serialMagazinesByDayOfWeek", HASH_SERIAL_MAGAZINES_BY_DAY_OF_WEEK, DayOfWeekVariables(today, lastCursor))
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<GraphQLResponse<LatestResponse>>()
        val panels = result.data.serialPerDayOfWeek.panels
        lastCursor = panels.pageInfo.endCursor
        val mangas = panels.edges.map { it.node.storyInfo.magazine.toSManga() }
        return MangasPage(mangas, panels.pageInfo.hasNextPage)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) lastCursor = null
        if (query.isNotBlank()) {
            return graphQLRequest(
                "magazinesByKeywordSearch",
                HASH_MAGAZINES_BY_KEYWORD_SEARCH,
                SearchVariables(query, lastCursor),
                false,
            ).newBuilder().tag("search").build()
        }

        val categoryFilter = filters.firstInstance<CategoryFilter>()
        return if (categoryFilter.value == "finished") {
            graphQLRequest("finishedMagazines", HASH_FINISHED_MAGAZINES, FinishedVariables(lastCursor)).newBuilder().tag("finished").build()
        } else {
            graphQLRequest("serialMagazinesByDayOfWeek", HASH_SERIAL_MAGAZINES_BY_DAY_OF_WEEK, DayOfWeekVariables(categoryFilter.value, lastCursor))
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = when (response.request.tag()) {
        "search" -> {
            val result = response.parseAs<GraphQLResponse<SearchResponse>>().data.searchComic
            val mangas = result.edges.map { it.node.toSManga() }
            lastCursor = result.pageInfo.endCursor
            MangasPage(mangas, result.pageInfo.hasNextPage)
        }

        "finished" -> {
            val result = response.parseAs<GraphQLResponse<FinishedResponseDto>>().data.magazinesByCategory.magazines
            val mangas = result.edges.map { it.node.toSManga() }
            lastCursor = result.pageInfo.endCursor
            MangasPage(mangas, result.pageInfo.hasNextPage)
        }

        else -> {
            latestUpdatesParse(response)
        }
    }

    // Details
    override fun getMangaUrl(manga: SManga) = "$baseUrl/web/magazine/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga) = graphQLRequest("magazineDetail", HASH_MAGAZINE_DETAIL, MagazineDetailVariables(manga.url))

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = response.parseAs<GraphQLResponse<DetailsResponse>>().data.magazine
        if (manga.isWebOnlySensitive == true) webOnlyAliases.add(manga.alias)
        return manga.toSManga()
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = graphQLRequest("storyInfoList", HASH_STORY_INFO_LIST, ChapterListVariables(manga.url, 9999, null)).newBuilder().tag(manga.url).build()

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.tag().toString()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val result = response.parseAs<GraphQLResponse<ChapterResponse>>()
        return result.data.magazine.storyInfos.edges.filter { !hideLocked || !it.node.isLocked }
            .map { it.node.toSChapter(slug) }
            .reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/web/reader/${chapter.url}/0"

    // Viewer
    override fun pageListRequest(chapter: SChapter): Request {
        val (alias, storyId) = "$baseUrl#${chapter.url}".toHttpUrl().fragment!!.split("/")
        return graphQLRequest("magazineStoryForReader", HASH_MAGAZINE_STORY_FOR_READER, ViewerVariables(alias, storyId), alias !in webOnlyAliases)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<GraphQLResponse<ViewerResponse>>().data.magazine.storyContents
        if (result.error != null) {
            throw Exception("Log in via WebView and get premium or purchase this chapter to read.")
        }

        val pageImages = result.pageImages!!
        val sign = pageImages.pageImageSign
        return buildList {
            (1..pageImages.pageCount).mapTo(this) { i ->
                val url = "${pageImages.pageImageBaseUrl}$i.jpg".toHttpUrl().newBuilder()
                    .encodedQuery(sign)
                    .setQueryParameter("w", "4999")
                    .build()
                    .toString()
                Page(i - 1, imageUrl = url)
            }
            result.afterword?.imageUrl?.let { imageUrl ->
                val url = imageUrl.toHttpUrl().newBuilder()
                    .setQueryParameter("w", "4999")
                    .build()
                    .toString()
                add(Page(size, imageUrl = url))
            }
        }
    }

    override fun getFilterList() = FilterList(CategoryFilter())

    private inline fun <reified T> graphQLRequest(operationName: String, hash: String, variables: T, useAppHeaders: Boolean = false): Request {
        val headers = headersBuilder()
            .set(
                "User-Agent",
                if (useAppHeaders) {
                    // Custom UA needed to read chapters.
                    "GanmaReader/10.7.0 Android"
                } else {
                    // Desktop UA needed to read web only chapters.
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
                },
            )
            .build()

        val extensions = Payload.Extensions(Payload.Extensions.PersistedQuery(1, hash))
        val payload = Payload(operationName, variables, extensions)
        val body = jsonInstance.encodeToString(Payload.serializer(serializer<T>()), payload).toRequestBody("application/json".toMediaType())
        return POST(apiUrl, headers, body)
    }

    private fun getLatestDay(): String {
        val calendar = Calendar.getInstance(jst)
        val days = arrayOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
        return days[calendar.get(Calendar.DAY_OF_WEEK) - 1]
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // Unsupported
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        // https://ganma.jp/web/_next/static/chunks/app/layout-fbbe5dd886d24cb7.js
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
