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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.encodeUtf8
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale

class KManga : HttpSource() {

    override val name = "K Manga"
    override val baseUrl = "https://kmanga.kodansha.com"
    override val lang = "en"
    override val supportsLatest = false

    private val apiUrl = "https://api.kmanga.kodansha.com"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val json: Json by injectLazy()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "XMLHttpRequest")
        .add("x-kmanga-platform", "3")

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .rateLimitHost(apiUrl.toHttpUrl(), 1)
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/ranking/all".toHttpUrl().newBuilder()
            .addQueryParameter("ranking_id", "12")
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "999") // 562 entries atm
            .build()

        val (birthday, expires) = getBirthdayCookie(url)

        val queryParams = url.queryParameterNames.associateWith { url.queryParameter(it)!! }
        val hash = generateHash(queryParams, birthday, expires)

        val newHeaders = headersBuilder()
            .add("x-kmanga-hash", hash)
            .build()

        return GET(url, newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val rankingResult = json.decodeFromString<RankingApiResponse>(response.body.string())
        val titleIds = rankingResult.ranking_title_list.map { it.id.toString() }

        if (titleIds.isEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val detailsUrl = "$apiUrl/title/list".toHttpUrl().newBuilder()
            .addQueryParameter("title_id_list", titleIds.joinToString(","))
            .build()

        val (birthday, expires) = getBirthdayCookie(detailsUrl)
        val queryParams = detailsUrl.queryParameterNames.associateWith { detailsUrl.queryParameter(it)!! }
        val hash = generateHash(queryParams, birthday, expires)

        val detailsHeaders = headersBuilder()
            .add("x-kmanga-hash", hash)
            .build()

        val detailsRequest = GET(detailsUrl, detailsHeaders)
        val detailsResponse = client.newCall(detailsRequest).execute()

        if (!detailsResponse.isSuccessful) {
            throw Exception("Failed to fetch title details: ${detailsResponse.code} - ${detailsResponse.body.string()}")
        }

        val detailsResult = json.decodeFromString<TitleListResponse>(detailsResponse.body.string())
        val mangas = detailsResult.title_list.map { manga ->
            SManga.create().apply {
                url = "/title/${manga.title_id}"
                title = manga.title_name
                thumbnail_url = manga.thumbnail_rect_image_url ?: manga.banner_image_url
            }
        }
        return MangasPage(mangas, false)
    }

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val prefix = "id:"

        return if (query.startsWith(prefix)) {
            val titleId = query.removePrefix(prefix)
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
        val genreFilter = filters.filterIsInstance<GenreFilter>().first()

        return when {
            query.isNotBlank() -> GET("$baseUrl/search/$query", headers)
            genreFilter.state != 0 -> GET("$baseUrl${genreFilter.toUriPart()}", headers)
            else -> popularMangaRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestUrl = response.request.url.toString()
        return if (requestUrl.contains("/search/") || requestUrl.contains("/search/genre/")) {
            val document = response.asJsoup()
            val mangas = document.select("ul.c-search-items li.c-search-items__item a.c-search-item").map(::searchMangaFromElement)
            MangasPage(mangas, false)
        } else {
            popularMangaParse(response)
        }
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href").substringBefore("/episode"))
        title = element.selectFirst(".c-search-item__ttl")!!.text()
        thumbnail_url = element.selectFirst("div.c-search-item__img img")?.attr("src")
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val nuxtData = document.selectFirst("script#__NUXT_DATA__")?.data()
            ?: throw Exception("Could not find Nuxt data")

        val rootArray = json.decodeFromString<JsonArray>(nuxtData)
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
            thumbnail_url = resolve(titleDetailsObject["banner_image_url"]!!).jsonPrimitive.content
            val genreIdRefs = resolve(titleDetailsObject["genre_id_list"]!!).jsonArray
            val genreIds = genreIdRefs.map { resolve(it).jsonPrimitive.content.toInt() }
            genre = genreIds.mapNotNull { genreMap[it] }.joinToString()
            status = SManga.ONGOING
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

        val rootArray = json.decodeFromString<JsonArray>(nuxtData)
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

        val result = json.decodeFromString<EpisodeListResponse>(apiResponse.body.string())

        return result.episode_list.map { chapter ->
            SChapter.create().apply {
                url = "/title/${chapter.title_id}/episode/${chapter.episode_id}"
                name = if (chapter.point > 0 && chapter.badge != 3 && chapter.rental_finish_time == null) {
                    "🔒 ${chapter.episode_name}"
                } else {
                    chapter.episode_name
                }
                date_upload = runCatching {
                    dateFormat.parse(chapter.start_time)!!.time
                }.getOrDefault(0L)
            }
        }.reversed()
    }

    private fun getBirthdayCookie(url: HttpUrl): Pair<String, String> {
        val cookies = client.cookieJar.loadForRequest(url)
        val birthdayCookie = cookies.firstOrNull { it.name == "birthday" }?.value

        return if (birthdayCookie != null) {
            try {
                val decoded = URLDecoder.decode(birthdayCookie, "UTF-8")
                val cookieData = json.decodeFromString<BirthdayCookie>(decoded)
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
            p(key, value)
        }

        val joinedParams = paramStrings.joinToString(",")
        val hash1 = joinedParams.encodeUtf8().sha256().hex()

        val cookieHash = p(birthday, expires)

        val finalString = "$hash1$cookieHash"
        return finalString.encodeUtf8().sha512().hex()
    }

    private fun p(key: String, value: String): String {
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
                        throw Exception("This chapter is locked. Log in and rent or purchase the chapter to view.")
                    }
                    throw Exception("HTTP error ${response.code}")
                }
                pageListParse(response)
            }
    }

    override fun pageListParse(response: Response): List<Page> {
        val apiResponse = json.decodeFromString<ViewerApiResponse>(response.body.string())
        val seed = apiResponse.scramble_seed
        return apiResponse.page_list.mapIndexed { index, imageUrl ->
            Page(index = index, imageUrl = "$imageUrl#scramble_seed=$seed")
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val episodeId = chapter.url.substringAfter("episode/")
        val url = "$apiUrl/web/episode/viewer?episode_id=$episodeId".toHttpUrl()

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

    // Unsupported
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
