package eu.kanade.tachiyomi.extension.ja.magazinepocket

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.encodeUtf8
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class MagazinePocket :
    HttpSource(),
    ConfigurableSource {
    override val name = "Magazine Pocket"
    private val domain = "pocket.shonenmagazine.com"
    override val baseUrl = "https://$domain"
    override val lang = "ja"
    override val supportsLatest = true
    override val versionId = 2

    private val apiUrl = "https://api.$domain"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    private val pageLimit = 25
    private val jst = TimeZone.getTimeZone("Asia/Tokyo")
    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("x-manga-platform", "3")

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code == 400 && request.url.pathSegments.last().contains("viewer")) {
                throw IOException("Log in via WebView and rent or purchase this chapter to read.")
            }
            response
        }
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * pageLimit
        val url = "$apiUrl/ranking/all".toHttpUrl().newBuilder()
            .addQueryParameter("ranking_id", "30")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "26")
            .build()
        return hashedGet(url)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val rankingResult = response.parseAs<RankingApiResponse>()
        val titleIds = rankingResult.rankingTitleList.map { it.id.toString().padStart(5, '0') }

        if (titleIds.isEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val hasNextPage = titleIds.size > pageLimit
        val mangaIdsToFetch = if (hasNextPage) titleIds.dropLast(1) else titleIds

        val detailsUrl = "$apiUrl/title/list".toHttpUrl().newBuilder()
            .addQueryParameter("title_id_list", mangaIdsToFetch.joinToString())
            .build()

        val detailsRequest = hashedGet(detailsUrl)
        val detailsResponse = client.newCall(detailsRequest).execute()
        val detailsResult = detailsResponse.parseAs<TitleListResponse>()
        val mangas = detailsResult.titleList.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        val dayOffset = page - 1
        if (dayOffset >= 14) return Observable.just(MangasPage(emptyList(), false))
        return Observable.fromCallable {
            val calendar = GregorianCalendar(jst).apply {
                time = Date()
                add(Calendar.DAY_OF_MONTH, -dayOffset)
            }

            val dateString = buildString {
                append(calendar.get(Calendar.YEAR))
                append('-')
                append((calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0'))
                append('-')
                append(calendar.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0'))
            }

            val url = "$apiUrl/web/top/updated/title".toHttpUrl().newBuilder()
                .addQueryParameter("base_date", dateString)
                .build()

            val request = hashedGet(url)
            val response = client.newCall(request).execute()
            val result = response.parseAs<TitleListResponse>().titleList
            val mangas = result.map { it.toSManga() }
            val hasNextPage = dayOffset < 13 && result.isNotEmpty()

            MangasPage(mangas, hasNextPage)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/web/search/title".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("limit", "99999")
                .build()
            return hashedGet(url)
        }

        val categoryFilter = filters.firstInstance<CategoryFilter>()
        val url = if (categoryFilter.type == "genre") {
            "$apiUrl/search/title".toHttpUrl().newBuilder()
                .addQueryParameter("genre_id", categoryFilter.value)
                .addQueryParameter("limit", "99999")
                .build()
        } else {
            val offset = (page - 1) * pageLimit
            "$apiUrl/ranking/all".toHttpUrl().newBuilder()
                .addQueryParameter("ranking_id", categoryFilter.value)
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("limit", "26")
                .build()
        }
        return hashedGet(url)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestUrl = response.request.url
        if (requestUrl.pathSegments.contains("search")) {
            val result = response.parseAs<TitleListResponse>()
            val mangas = result.titleList.map { it.toSManga() }.reversed()
            return MangasPage(mangas, false)
        }
        return popularMangaParse(response)
    }

    // Details
    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        val titleId = (baseUrl + manga.url).toHttpUrl().pathSegments.last()
        val url = "$apiUrl/web/title/detail".toHttpUrl().newBuilder()
            .addQueryParameter("title_id", titleId)
            .build()
        return hashedGet(url)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<DetailResponse>().webTitle
        return SManga.create().apply {
            title = result.titleName
            author = result.authorText
            description = result.introductionText
            thumbnail_url = result.thumbnailImageUrl ?: result.bannerImageUrl ?: result.thumbnailRectImageUrl
            if (result.genreIdList.isNotEmpty()) {
                val genreApiUrl = "$apiUrl/genre/list".toHttpUrl().newBuilder()
                    .addQueryParameter("genre_id_list", result.genreIdList.joinToString())
                    .build()

                val genreRequest = hashedGet(genreApiUrl)
                val genreResponse = client.newCall(genreRequest).execute()

                if (genreResponse.isSuccessful) {
                    val genreResult = genreResponse.parseAs<GenreListResponse>()
                    genre = genreResult.genreList.joinToString { it.genreName }
                }
            }
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val resultIds = response.parseAs<DetailResponse>()
        val episodeIds = resultIds.webTitle.episodeIdList.map { it.toString() }

        if (episodeIds.isEmpty()) return emptyList()

        val formBody = FormBody.Builder()
            .add("episode_id_list", episodeIds.joinToString())
            .build()

        val params = (0 until formBody.size).associate { formBody.name(it) to formBody.value(it) }
        val hash = generateHash(params)

        val postHeaders = headersBuilder()
            .add("x-manga-hash", hash)
            .build()

        val apiRequest = POST("$apiUrl/episode/list", postHeaders, formBody)
        val apiResponse = client.newCall(apiRequest).execute()
        val result = apiResponse.parseAs<EpisodeListResponse>()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)

        return result.episodeList
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter(dateFormat) }
            .reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val episodeId = (baseUrl + chapter.url).toHttpUrl().pathSegments.last()
        val url = "$apiUrl/web/episode/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("episode_id", episodeId)
            .build()
        return hashedGet(url)
    }

    override fun pageListParse(response: Response): List<Page> {
        val apiResponse = response.parseAs<ViewerApiResponse>()
        val seed = apiResponse.scrambleSeed
        val titleId = apiResponse.titleId
        val episodeId = apiResponse.episodeId
        return apiResponse.pageList.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = "$imageUrl#$seed:$titleId:$episodeId")
        }
    }

    private fun generateHash(params: Map<String, String>, birthday: String = "", expires: String = ""): String {
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

    private fun hashedGet(url: HttpUrl): Request {
        val queryParams = url.queryParameterNames.associateWith { url.queryParameter(it)!! }
        val hash = generateHash(queryParams)
        val newHeaders = headersBuilder()
            .add("x-manga-hash", hash)
            .build()
        return GET(url, newHeaders)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Search query will ignore genre filter"),
        CategoryFilter(),
    )

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }

    // Unsupported
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
