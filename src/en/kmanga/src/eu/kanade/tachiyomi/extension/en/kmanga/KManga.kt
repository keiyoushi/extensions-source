package eu.kanade.tachiyomi.extension.en.kmanga

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.ByteString.Companion.encodeUtf8
import java.io.IOException
import java.net.URLDecoder

@Source
abstract class KManga :
    KeiSource(),
    ConfigurableSource {
    private val domain = baseUrl.toHttpUrl().host
    private val apiUrl = "https://api.$domain"
    private val pageLimit = 25
    private val preferences by getPreferencesLazy()

    override fun Headers.Builder.configureHeaders() = add("X-Kmanga-Platform", "3")

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code == 400) {
                response.close()
                val error = if (request.url.pathSegments.last().contains("viewer")) {
                    "Log in via WebView and rent or purchase this chapter to read."
                } else {
                    reloadUserId = true
                    "Open WebView and retry"
                }

                throw IOException(error)
            }
            response
        }
    }

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val offset = (page - 1) * pageLimit
        val url = "$apiUrl/ranking/all".toHttpUrl().newBuilder()
            .addQueryParameter("ranking_id", "12")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", (pageLimit + 1).toString())
            .build()

        val rankingResult = hashedReq(url).parseAs<RankingApiResponse>()
        val titleIds = rankingResult.rankingTitleList.map { it.id.toString() }
        if (titleIds.isEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val hasNextPage = titleIds.size > pageLimit
        val mangaIdsToFetch = if (hasNextPage) titleIds.dropLast(1) else titleIds
        val detailsUrl = "$apiUrl/title/list".toHttpUrl().newBuilder()
            .addQueryParameter("title_id_list", mangaIdsToFetch.joinToString(","))
            .build()

        val result = hashedReq(detailsUrl).parseAs<TitleListResponse>()
        val mangas = result.titleList.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = hashedReq("$apiUrl/title/weekly".toHttpUrl()).parseAs<LatestResponse>()
        val todayIdList = result.weeklyList.first { it.weekdayIndex == result.todayWeekdayIndex }.titleIdList
        val titleById = result.titleList.associateBy { it.titleId }
        val mangas = todayIdList.mapNotNull { titleById[it]?.toSManga() }
        return MangasPage(mangas, false)
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchUrl = "$apiUrl/search/title".toHttpUrl()
        val url = if (query.isNotBlank()) {
            searchUrl.newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("limit", "99999")
                .build()
        } else {
            val genre = filters.firstInstance<GenreFilter>()
            searchUrl.newBuilder()
                .addQueryParameter("genre_id", genre.value)
                .addQueryParameter("limit", "99999")
                .build()
        }

        val result = hashedReq(url).parseAs<TitleListResponse>()
        val mangas = result.titleList.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != domain) throw Exception("Unsupported Url")
        val titleId = url.pathSegments.getOrNull(1) ?: throw Exception("Unsupported Url")
        val manga = SManga.create().apply {
            this.url = "/title/$titleId"
        }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    private suspend fun fetchGenreNames(genreIds: List<Int>): String? {
        val url = "$apiUrl/genre/list".toHttpUrl().newBuilder()
            .addQueryParameter("genre_id_list", genreIds.joinToString())
            .build()
        return hashedReq(url).parseAs<GenreListResponse>().genreList?.joinToString { it.genreName }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val titleId = (baseUrl + manga.url).toHttpUrl().pathSegments.last()
        val url = "$apiUrl/web/title/detail".toHttpUrl().newBuilder()
            .addQueryParameter("title_id", titleId)
            .build()

        val webTitle = hashedReq(url).parseAs<DetailResponse>().webTitle
        val genre = webTitle.genreIdList?.takeIf { it.isNotEmpty() }?.let { fetchGenreNames(it) }

        val updatedChapters = if (fetchChapters) {
            val episodeIds = webTitle.episodeIdList.map(Int::toString)
            if (episodeIds.isEmpty()) {
                emptyList()
            } else {
                val formBody = FormBody.Builder()
                    .add("episode_id_list", episodeIds.joinToString(","))
                    .build()
                val result = hashedReq("$apiUrl/episode/list".toHttpUrl(), formBody).parseAs<EpisodeListResponse>()

                result.episodeList
                    .filter { !hideLocked || !it.isLocked }
                    .map { it.toSChapter() }
                    .reversed()
            }
        } else {
            chapters
        }

        return SMangaUpdate(
            webTitle.toSManga(titleId, genre),
            updatedChapters,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val episodeId = (baseUrl + chapter.url).toHttpUrl().pathSegments.last()
        val url = "$apiUrl/web/episode/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("episode_id", episodeId)
            .build()

        val result = hashedReq(url).parseAs<ViewerApiResponse>()
        return result.pageList.mapIndexed { index, page ->
            Page(index, imageUrl = "$page#${result.scrambleSeed}:${result.titleId}:${result.episodeId}")
        }
    }

    private fun getBirthdayCookie(url: HttpUrl): Pair<String, String> {
        val cookies = client.cookieJar.loadForRequest(url)
        val birthdayCookie = cookies.firstOrNull { it.name == "birthday" }?.value

        return if (birthdayCookie != null) {
            try {
                val decoded = URLDecoder.decode(birthdayCookie, "UTF-8")
                val cookieData = decoded.parseAs<BirthdayCookie>()
                cookieData.value to cookieData.expires.toString()
            } catch (_: Exception) {
                // Fallback to default if cookie is malformed
                "2000-01" to (System.currentTimeMillis() / 1000 + 315360000).toString()
            }
        } else {
            // Default for logged-out users or users without the cookie to bypass age restrictions
            "2000-01" to (System.currentTimeMillis() / 1000 + 315360000).toString()
        }
    }

    // https://kmanga.kodansha.com/_nuxt/vl9so/entry-CSwIbMdW.js
    private fun generateHash(params: Map<String, String>, birthday: String, expires: String): String {
        val paramStrings = params.toSortedMap().map { (key, value) ->
            getHashedParam(key, value)
        }

        val joinedParams = paramStrings.joinToString(",")
        val hash1 = joinedParams.encodeUtf8().sha256().hex()
        val cookieHash = getHashedParam(birthday, expires)
        val finalString = "$hash1$cookieHash"
        return finalString.encodeUtf8().sha512().hex()
    }

    private fun getHashedParam(key: String, value: String): String {
        val keyHash = key.encodeUtf8().sha256().hex()
        val valueHash = value.encodeUtf8().sha512().hex()
        return "${keyHash}_$valueHash"
    }

    private var userId: Int? = null
    private var reloadUserId = false

    private suspend fun hashedReq(url: HttpUrl, body: FormBody? = null): Response {
        if (reloadUserId || userId == null) setUserId()

        val (birthday, expires) = getBirthdayCookie(url)
        val params = if (body != null) {
            (0 until body.size).associate { body.name(it) to body.value(it) }
        } else {
            url.queryParameterNames.associateWith {
                url.queryParameter(it)!!
            }
        }

        val hash = generateHash(params, birthday, expires)
        val newHeaders = headersBuilder()
            .add("x-kmanga-client-id", userId.toString())
            .add("x-kmanga-is-crawler", "false")
            .add("X-Kmanga-Hash", hash)
            .build()

        return if (body != null) {
            client.post(url, newHeaders, body)
        } else {
            client.get(url, newHeaders)
        }
    }

    private suspend fun setUserId() {
        reloadUserId = false
        val account = getLocalStorage(baseUrl, "account")?.parseAs<LocalStorageAccount>()
        userId = if (account?.isLoggedIn == true) {
            account.checkedTicketExpiredList?.firstOrNull()?.userId ?: 0
        } else {
            0
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // Filters
    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("NOTE: Search query will ignore genre filter"),
        GenreFilter(),
    )

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
