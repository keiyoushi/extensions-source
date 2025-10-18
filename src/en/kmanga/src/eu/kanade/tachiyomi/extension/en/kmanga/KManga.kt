package eu.kanade.tachiyomi.extension.en.kmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.encodeUtf8
import rx.Observable
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class KManga : HttpSource() {

    override val name = "K Manga"
    override val baseUrl = "https://kmanga.kodansha.com"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "https://api.kmanga.kodansha.com"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val pageLimit = 25
    private val searchLimit = 25

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "XMLHttpRequest")
        .add("x-kmanga-platform", "3")

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .rateLimitHost(apiUrl.toHttpUrl(), 1)
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * pageLimit
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments("ranking/all")
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

        val detailsUrl = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments("title/list")
            .addQueryParameter("title_id_list", mangaIdsToFetch.joinToString(","))
            .build()

        val detailsRequest = hashedGet(detailsUrl)
        val detailsResponse = client.newCall(detailsRequest).execute()

        if (!detailsResponse.isSuccessful) {
            throw Exception("Failed to fetch title details: ${detailsResponse.code} - ${detailsResponse.body.string()}")
        }

        val detailsResult = detailsResponse.parseAs<TitleListResponse>()
        val mangas = detailsResult.titleList.map { manga ->
            SManga.create().apply {
                url = "/title/${manga.titleId}"
                title = manga.titleName
                thumbnail_url = manga.thumbnailImageUrl ?: manga.bannerImageUrl
            }
        }
        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val calendar = GregorianCalendar(TimeZone.getTimeZone("Asia/Tokyo")).apply {
            time = Date()

            add(Calendar.DAY_OF_MONTH, -(page - 1))

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

        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments("web/top/updated/title")
            .addQueryParameter("base_date", dateString)
            .build()

        return hashedGet(url)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<LatestTitleListResponse>()
        val manga = result.titleList.map { manga ->
            SManga.create().apply {
                url = "/title/${manga.titleId}"
                title = manga.titleName
                thumbnail_url = manga.thumbnailImageUrl
            }
        }
        return MangasPage(manga, true)
    }

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) {
            val titleId = query.removePrefix(PREFIX_SEARCH)
            fetchMangaDetails(
                SManga.create().apply { url = "/title/$titleId" },
            ).map { manga ->
                MangasPage(listOf(manga.apply { url = "/title/$titleId" }), false)
            }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genreFilter = filters.firstInstance<GenreFilter>()

        if (query.isNotBlank()) {
            val offset = (page - 1) * searchLimit
            val url = apiUrl.toHttpUrl().newBuilder()
                .addPathSegments("search/title")
                .addQueryParameter("keyword", query)
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("limit", searchLimit.toString())
                .build()
            return hashedGet(url)
        }

        if (genreFilter.state != 0) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments(genreFilter.toUriPart().removePrefix("/"))
                .build()
            return GET(url, headers)
        }
        return popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.host.contains("api.")) {
            val result = response.parseAs<SearchApiResponse>()
            val mangas = result.titleList.map { manga ->
                SManga.create().apply {
                    url = "/title/${manga.titleId}"
                    title = manga.titleName
                    thumbnail_url = manga.thumbnailImageUrl
                }
            }
            return MangasPage(mangas, mangas.size >= searchLimit)
        }

        if (response.request.url.toString().contains("/search/genre/")) {
            val document = response.asJsoup()
            val nuxtData = document.selectFirst("script#__NUXT_DATA__")?.data()
                ?: return MangasPage(emptyList(), false)

            val rootArray = nuxtData.parseAs<JsonArray>()
            fun resolve(ref: JsonElement): JsonElement = rootArray[ref.jsonPrimitive.content.toInt()]

            val genreResultObject = rootArray.firstOrNull { it is JsonObject && "title_list" in it.jsonObject }
                ?: return MangasPage(emptyList(), false)

            val mangaRefs = resolve(genreResultObject.jsonObject["title_list"]!!).jsonArray

            val mangas = mangaRefs.map { ref ->
                val mangaObject = resolve(ref).jsonObject
                SManga.create().apply {
                    url = "/title/${resolve(mangaObject["title_id"]!!).jsonPrimitive.content}"
                    title = resolve(mangaObject["title_name"]!!).jsonPrimitive.content
                    thumbnail_url = mangaObject["thumbnail_image_url"]?.let { resolve(it).jsonPrimitive.content }
                }
            }
            return MangasPage(mangas, false)
        }
        return popularMangaParse(response)
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val nuxtData = document.selectFirst("script#__NUXT_DATA__")?.data()
            ?: throw Exception("Could not find Nuxt data")

        val rootArray = nuxtData.parseAs<JsonArray>()
        fun resolve(ref: JsonElement): JsonElement = rootArray[ref.jsonPrimitive.content.toInt()]

        val titleDetailsObject = rootArray.first { it is JsonObject && it.jsonObject.containsKey("title_in_japanese") }.jsonObject

        val genreMap = buildMap {
            rootArray.forEach { element ->
                if (element is JsonObject && element.jsonObject.containsKey("genre_id")) {
                    val genreObject = element.jsonObject
                    val id = resolve(genreObject["genre_id"]!!).jsonPrimitive.content.toInt()
                    val name = resolve(genreObject["genre_name"]!!).jsonPrimitive.content
                    put(id, name)
                }
            }
        }

        return SManga.create().apply {
            title = resolve(titleDetailsObject["title_name"]!!).jsonPrimitive.content
            author = resolve(titleDetailsObject["author_text"]!!).jsonPrimitive.content
            description = resolve(titleDetailsObject["introduction_text"]!!).jsonPrimitive.content
            thumbnail_url = titleDetailsObject["thumbnail_image_url"]?.let { resolve(it).jsonPrimitive.content }
            val genreIdRefs = resolve(titleDetailsObject["genre_id_list"]!!).jsonArray
            val genreIds = genreIdRefs.map { resolve(it).jsonPrimitive.content.toInt() }
            genre = genreIds.mapNotNull { genreMap[it] }.joinToString()
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val nuxtData = document.selectFirst("script#__NUXT_DATA__")?.data()
            ?: throw Exception("Could not find Nuxt data")

        val rootArray = nuxtData.parseAs<JsonArray>()
        fun resolve(ref: JsonElement): JsonElement = rootArray[ref.jsonPrimitive.content.toInt()]

        val titleDetailsObject = rootArray.first { it is JsonObject && it.jsonObject.containsKey("episode_id_list") }.jsonObject
        val episodeIdRefs = resolve(titleDetailsObject["episode_id_list"]!!).jsonArray
        val episodeIds = episodeIdRefs.map { resolve(it).jsonPrimitive.content }

        val (birthday, expires) = getBirthdayCookie(response.request.url)

        val formBody = FormBody.Builder()
            .add("episode_id_list", episodeIds.joinToString(","))
            .build()

        val params = (0 until formBody.size).associate { formBody.name(it) to formBody.value(it) }
        val hash = generateHash(params, birthday, expires)

        val postHeaders = headersBuilder()
            .add("Accept", "application/json")
            .add("Content-Type", "application/x-www-form-urlencoded")
            .add("Origin", baseUrl)
            .add("x-kmanga-is-crawler", "false")
            .add("x-kmanga-hash", hash)
            .build()

        val apiRequest = POST("$apiUrl/episode/list", postHeaders, formBody)
        val apiResponse = client.newCall(apiRequest).execute()

        if (!apiResponse.isSuccessful) {
            throw Exception("API request failed with code ${apiResponse.code}: ${apiResponse.body.string()}")
        }

        val result = apiResponse.parseAs<EpisodeListResponse>()

        return result.episodeList.map { chapter ->
            SChapter.create().apply {
                url = "/title/${chapter.titleId}/episode/${chapter.episodeId}"
                name = if (chapter.point > 0 && chapter.badge != 3 && chapter.rentalFinishTime == null) {
                    "🔒 ${chapter.episodeName}"
                } else {
                    chapter.episodeName
                }
                date_upload = dateFormat.tryParse(chapter.startTime)
            }
        }.reversed()
    }

    private fun getBirthdayCookie(url: HttpUrl): Pair<String, String> {
        val cookies = client.cookieJar.loadForRequest(url)
        val birthdayCookie = cookies.firstOrNull { it.name == "birthday" }?.value

        return if (birthdayCookie != null) {
            try {
                val decoded = URLDecoder.decode(birthdayCookie, "UTF-8")
                val cookieData = decoded.parseAs<BirthdayCookie>()
                cookieData.value to cookieData.expires.toString()
            } catch (e: Exception) {
                // Fallback to default if cookie is malformed
                "2000-01" to (System.currentTimeMillis() / 1000 + 315360000).toString()
            }
        } else {
            // Default for logged out users or users without the cookie to bypass age restrictions
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

    // Pages
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservable()
            .map { response ->
                if (!response.isSuccessful) {
                    if (response.code == 400) {
                        throw Exception("This chapter is locked. Log in via WebView and rent or purchase the chapter to read.")
                    }
                    throw Exception("HTTP error ${response.code}")
                }
                pageListParse(response)
            }
    }

    override fun pageListParse(response: Response): List<Page> {
        val apiResponse = response.parseAs<ViewerApiResponse>()
        val seed = apiResponse.scrambleSeed
        return apiResponse.pageList.mapIndexed { index, imageUrl ->
            Page(index = index, imageUrl = "$imageUrl#scramble_seed=$seed")
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val episodeId = chapter.url.substringAfter("episode/")
        val url = "$apiUrl/web/episode/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("episode_id", episodeId)
            .build()

        return hashedGet(url)
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

    // Filters
    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Search query will ignore genre filter"),
        GenreFilter(),
    )

    private class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("All", "/ranking"),
            Pair("Romance･Romcom", "/search/genre/1"),
            Pair("Horror･Mystery･Suspense", "/search/genre/2"),
            Pair("Gag･Comedy･Slice-of-Life", "/search/genre/3"),
            Pair("SF･Fantasy", "/search/genre/4"),
            Pair("Sports", "/search/genre/5"),
            Pair("Drama", "/search/genre/6"),
            Pair("Outlaws･Underworld･Punks", "/search/genre/7"),
            Pair("Action･Battle", "/search/genre/8"),
            Pair("Isekai･Super Powers", "/search/genre/9"),
            Pair("One-off Books", "/search/genre/10"),
            Pair("Shojo/josei", "/search/genre/11"),
            Pair("Yaoi/BL", "/search/genre/12"),
            Pair("LGBTQ", "/search/genre/13"),
            Pair("Yuri/GL", "/search/genre/14"),
            Pair("Anime", "/search/genre/15"),
            Pair("Award Winner", "/search/genre/16"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
    }

    // Unsupported
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
