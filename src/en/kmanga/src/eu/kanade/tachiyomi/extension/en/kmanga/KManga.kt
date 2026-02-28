package eu.kanade.tachiyomi.extension.en.kmanga

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
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class KManga :
    HttpSource(),
    ConfigurableSource {
    override val name = "K Manga"
    private val domain = "kmanga.kodansha.com"
    override val baseUrl = "https://$domain"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "https://api.$domain"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    private val pageLimit = 25
    private val jst = TimeZone.getTimeZone("Asia/Tokyo")
    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("x-kmanga-platform", "3")

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
            .addQueryParameter("ranking_id", "12")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", (pageLimit + 1).toString())
            .build()
        return hashedGet(url)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val rankingResult = response.parseAs<RankingApiResponse>()
        val titleIds = rankingResult.rankingTitleList.map { it.id.toString() }

        if (titleIds.isEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val hasNextPage = titleIds.size > pageLimit
        val mangaIdsToFetch = if (hasNextPage) titleIds.dropLast(1) else titleIds

        val detailsUrl = "$apiUrl/title/list".toHttpUrl().newBuilder()
            .addQueryParameter("title_id_list", mangaIdsToFetch.joinToString(","))
            .build()

        val detailsRequest = hashedGet(detailsUrl)
        val detailsResponse = client.newCall(detailsRequest).execute()
        val result = detailsResponse.parseAs<TitleListResponse>()
        val mangas = result.titleList.map { it.toSManga() }.reversed()
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        if (page > 1) return Observable.just(MangasPage(emptyList(), false))
        return Observable.fromCallable {
            val mangas = ArrayList<SManga>()
            var dayOffset = 0

            while (true) {
                val calendar = GregorianCalendar(jst).apply {
                    time = Date()
                    add(Calendar.DAY_OF_MONTH, -dayOffset)

                    // Manga seems to usually update at 10 AM JST, so if we're before that time we should go
                    // back a day since we don't expect there to have been any updates today yet.
                    if (get(Calendar.HOUR_OF_DAY) < 10) {
                        add(Calendar.DAY_OF_MONTH, -1)
                    }
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

                if (result.isEmpty()) break

                mangas.addAll(result.map { it.toSManga() }.reversed())
                dayOffset++
            }

            MangasPage(mangas, false)
        }
    }

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH)) {
            val titleId = query.removePrefix(PREFIX_SEARCH)
            fetchMangaDetails(
                SManga.create().apply { url = "/title/$titleId" },
            ).map {
                MangasPage(listOf(it.apply { url = "/title/$titleId" }), false)
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = "$apiUrl/search/title".toHttpUrl()
        if (query.isNotBlank()) {
            val url = searchUrl.newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("limit", "99999")
                .build()
            return hashedGet(url)
        }

        val filters = filters.firstInstance<GenreFilter>()
        val url = searchUrl.newBuilder()
            .addQueryParameter("genre_id", filters.value)
            .addQueryParameter("limit", "99999")
            .build()
        return hashedGet(url)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<TitleListResponse>()
        val mangas = result.titleList.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val titleId = (baseUrl + manga.url).toHttpUrl().pathSegments.last()
        val url = "$apiUrl/web/title/detail".toHttpUrl().newBuilder()
            .addQueryParameter("title_id", titleId)
            .build()
        return hashedGet(url)
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<DetailResponse>().webTitle
        return SManga.create().apply {
            title = result.titleName
            author = result.authorText
            description = buildString {
                append(result.introductionText)
                if (!result.nextUpdatedText.isNullOrBlank()) {
                    append("\n\n${result.nextUpdatedText}")
                }
                if (!result.titleInJapanese.isNullOrBlank()) {
                    append("\n\nJapanese Title: ${result.titleInJapanese}")
                }
            }
            thumbnail_url = result.thumbnailImageUrl ?: result.bannerImageUrl ?: result.thumbnailRectImageUrl
            result.genreIdList?.let { genres ->
                if (genres.isNotEmpty()) {
                    val genreApiUrl = "$apiUrl/genre/list".toHttpUrl().newBuilder()
                        .addQueryParameter("genre_id_list", result.genreIdList.joinToString())
                        .build()

                    val genreRequest = hashedGet(genreApiUrl)
                    val genreResponse = client.newCall(genreRequest).execute()

                    if (genreResponse.isSuccessful) {
                        val genreResult = genreResponse.parseAs<GenreListResponse>()
                        genre = genreResult.genreList?.joinToString { it.genreName }
                    }
                }
            }
        }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val resultIds = response.parseAs<DetailResponse>()
        val episodeIds = resultIds.webTitle.episodeIdList.map { it.toString() }

        if (episodeIds.isEmpty()) return emptyList()

        val (birthday, expires) = getBirthdayCookie(response.request.url)
        val formBody = FormBody.Builder()
            .add("episode_id_list", episodeIds.joinToString(","))
            .build()

        val params = (0 until formBody.size).associate { formBody.name(it) to formBody.value(it) }
        val hash = generateHash(params, birthday, expires)

        val postHeaders = headersBuilder()
            .add("x-kmanga-hash", hash)
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
        val results = response.parseAs<ViewerApiResponse>()
        val seed = results.scrambleSeed
        return results.pageList.mapIndexed { index, page ->
            Page(index, imageUrl = "$page#scramble_seed=$seed")
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

    private fun hashedGet(url: HttpUrl): Request {
        val (birthday, expires) = getBirthdayCookie(url)
        val queryParams = url.queryParameterNames.associateWith { url.queryParameter(it)!! }
        val hash = generateHash(queryParams, birthday, expires)
        val newHeaders = headersBuilder()
            .add("x-kmanga-hash", hash)
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

    // Filters
    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Search query will ignore genre filter"),
        GenreFilter(),
    )

    private class GenreFilter :
        SelectFilter(
            "Genres",
            arrayOf(
                Pair("Romance･Romcom", "1"),
                Pair("Horror･Mystery･Suspense", "2"),
                Pair("Gag･Comedy･Slice-of-Life", "3"),
                Pair("SF･Fantasy", "4"),
                Pair("Sports", "5"),
                Pair("Drama", "6"),
                Pair("Outlaws･Underworld･Punks", "7"),
                Pair("Action･Battle", "8"),
                Pair("Isekai･Super Powers", "9"),
                Pair("One-off Books", "10"),
                Pair("Shojo/josei", "11"),
                Pair("Yaoi/BL", "12"),
                Pair("LGBTQ", "13"),
                Pair("Yuri/GL", "14"),
                Pair("Anime", "15"),
                Pair("Award Winner", "16"),
            ),
        )

    private open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val value: String
            get() = vals[state].second
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        const val PREFIX_SEARCH = "id:"
    }

    // Unsupported
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
