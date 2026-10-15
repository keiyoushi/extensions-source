package eu.kanade.tachiyomi.extension.ja.cycomi

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
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl.Companion.FORCE_NETWORK
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

@Source
abstract class CyComi :
    KeiSource(),
    ConfigurableSource {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://web.$domain/api"
    private val jst = ZoneId.of("Asia/Tokyo")
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$apiUrl/titleRanking/title/list?categoryId=1")
            .parseAs<Data<List<TitleList>>>().data
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val day = ZonedDateTime.now(jst).minusHours(12).dayOfWeek
        val dayValue = if (day == DayOfWeek.SUNDAY) 0 else day.value
        return client.get("$apiUrl/title/serialization/list/$dayValue").toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val url = "$apiUrl/search/list/1".toHttpUrl().newBuilder()
                .addQueryParameter("word", query)
                .build()
            return client.get(url).toMangasPage()
        }

        val filter = filters.firstInstance<CategoryFilter>()
        return client.get("$apiUrl/title/serialization/list/${filter.value}").toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val result = this.parseAs<Data<MangaResponse>>()
        val mangas = result.data.titles.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            val url = "$apiUrl/title/detail".toHttpUrl().newBuilder()
                .addQueryParameter("titleId", manga.url)
                .build()
            client.get(url).parseAs<Data<DetailsResponse>>().data.toSManga()
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = async {
            if (!fetchChapters) return@async chapters

            val lockedById = async {
                // server-side rate limit is stricter for non-logged-in users; check if the user is logged in here
                val loggedIn = client.cookieJar.loadForRequest(apiUrl.toHttpUrl()).any { it.name == "sessionId" }
                val statusPaths = if (loggedIn) {
                    listOf("user/chapter/status/list", "chapter/status/list")
                } else {
                    listOf("chapter/status/list")
                }
                statusPaths.firstNotNullOfOrNull { path ->
                    val url = "$apiUrl/$path".toHttpUrl().newBuilder()
                        .addQueryParameter("titleId", manga.url)
                        .build()
                    runCatching {
                        client.get(url, FORCE_NETWORK)
                            .parseAs<Data<List<StatusResponse>>>().data
                            .associate { it.id to it.isLocked }
                    }.getOrNull()
                }.orEmpty()
            }

            val chapterData = async {
                val url = "$apiUrl/chapter/paginatedList".toHttpUrl().newBuilder()
                    .addQueryParameter("titleId", manga.url)
                    .addQueryParameter("sort", "2")
                    .build()
                client.get(url).parseAs<Data<List<ChapterListResponse>>>().data
            }

            val locked = lockedById.await()
            chapterData.await()
                .associateWith { locked[it.id] ?: false }
                .filter { !hideLocked || !it.value }
                .map { (chapter, isLocked) -> chapter.toSChapter(isLocked) }
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/viewer/chapter/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = ViewerRequestBody(chapter.memo["titleId"]!!.int, chapter.url.toInt()).toJsonRequestBody()
        val pages = client.post("$apiUrl/chapter/page/list", body).parseAs<Data<ViewerResponse>>().data.pages
        if (pages.isEmpty()) {
            throw Exception("Log in via WebView and rent or purchase this chapter to read.")
        }

        return pages.map {
            Page(it.pageNumber, imageUrl = "${it.image}#decrypt")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Paid Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(),
    )

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
