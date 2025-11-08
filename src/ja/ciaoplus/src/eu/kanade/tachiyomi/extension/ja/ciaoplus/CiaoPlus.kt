package eu.kanade.tachiyomi.extension.ja.ciaoplus

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.encodeUtf8
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class CiaoPlus : HttpSource() {
    override val name = "Ciao Plus"
    override val baseUrl = "https://ciao.shogakukan.co.jp"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl = "https://api.ciao.shogakukan.co.jp"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)
    private val latestRequestDateFormat = SimpleDateFormat("yyyyMMdd", Locale.JAPAN)
    private val latestResponseDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN)
    private val pageLimit = 25

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * pageLimit
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments("ranking/all")
            .addQueryParameter("platform", "3")
            .addQueryParameter("ranking_id", "1")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "51")
            .addQueryParameter("is_top", "0")
            .build()
        return hashedGet(url)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val requestUrl = response.request.url.toString()
        val rankingResult = response.parseAs<RankingApiResponse>()
        val titleIds = rankingResult.rankingTitleList.map { it.id.toString().padStart(5, '0') }
        if (titleIds.isEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val hasNextPage = titleIds.size > pageLimit
        val mangaIdsToFetch = if (hasNextPage) titleIds.dropLast(1) else titleIds
        val detailsUrl = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments("title/list")
            .addQueryParameter("platform", "3")
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
                val paddedId = manga.titleId.toString().padStart(5, '0')
                url = "/comics/title/$paddedId"
                title = manga.titleName
                thumbnail_url = manga.thumbnailImageUrl
            }
        }

        if (requestUrl.contains("/genre/")) {
            return MangasPage(mangas.reversed(), hasNextPage)
        }
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val calendar = GregorianCalendar(TimeZone.getTimeZone("Asia/Tokyo")).apply {
            time = Date()
            add(Calendar.DAY_OF_MONTH, -(page - 1))
        }
        val dateString = latestRequestDateFormat.format(calendar.time)

        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments("web/title/ids")
            .addQueryParameter("updated_at", dateString)
            .addQueryParameter("platform", "3")
            .build()
        return hashedGet(url)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<LatestTitleListResponse>()
        val today = GregorianCalendar(TimeZone.getTimeZone("Asia/Tokyo")).time
        val mangas = result.updateEpisodeTitles
            .filterKeys {
                if (it.startsWith("2099")) return@filterKeys false
                val entryDate = latestResponseDateFormat.parse(it)
                !entryDate!!.after(today)
            }
            .flatMap { it.value }
            .distinctBy { it.titleId }
            .map { manga ->
                SManga.create().apply {
                    val paddedId = manga.titleId.toString().padStart(5, '0')
                    url = "/comics/title/$paddedId"
                    title = manga.titleName
                    thumbnail_url = manga.thumbnailImageUrl
                }
            }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = apiUrl.toHttpUrl().newBuilder()
                .addPathSegments("search/title")
                .addQueryParameter("keyword", query)
                .addQueryParameter("limit", "99999")
                .addQueryParameter("platform", "3")
                .build()
            return hashedGet(url)
        }

        val genreFilter = filters.firstInstance<GenreFilter>()
        val uriPart = genreFilter.toUriPart()
        val url = if (uriPart.startsWith("/genre/")) {
            val genreId = uriPart.substringAfter("/genre/")
            apiUrl.toHttpUrl().newBuilder()
                .addPathSegments("search/title")
                .addQueryParameter("platform", "3")
                .addQueryParameter("genre_id", genreId)
                .addQueryParameter("limit", "99999")
                .build()
        } else {
            val rankingId = uriPart.substringAfter("/ranking/")
            val offset = (page - 1) * pageLimit
            apiUrl.toHttpUrl().newBuilder()
                .addPathSegments("ranking/all")
                .addQueryParameter("platform", "3")
                .addQueryParameter("ranking_id", rankingId)
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("limit", "51")
                .addQueryParameter("is_top", "0")
                .build()
        }
        return hashedGet(url)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestUrl = response.request.url.toString()
        if (requestUrl.contains("/search/title")) {
            val result = response.parseAs<SearchApiResponse>()
            val mangas = result.searchTitleList.map { manga ->
                SManga.create().apply {
                    val paddedId = manga.titleId.toString().padStart(5, '0')
                    url = "/comics/title/$paddedId"
                    title = manga.titleName
                    thumbnail_url = manga.bannerImageUrl
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

        val titleDetailsObject = rootArray
            .filterIsInstance<JsonObject>()
            .findLast { "title_name" in it && "author_text" in it && "introduction_text" in it }
            ?.jsonObject

        return SManga.create().apply {
            title = resolve(titleDetailsObject?.get("title_name")!!).jsonPrimitive.content
            author = resolve(titleDetailsObject["author_text"]!!).jsonPrimitive.content
            description = resolve(titleDetailsObject["introduction_text"]!!).jsonPrimitive.content
            val genreIdRefs = resolve(titleDetailsObject["genre_id_list"]!!).jsonArray
            val genreIds = genreIdRefs.map { resolve(it).jsonPrimitive.content.toInt() }

            if (genreIds.isNotEmpty()) {
                val genreApiUrl = apiUrl.toHttpUrl().newBuilder()
                    .addPathSegments("genre/list")
                    .addQueryParameter("platform", "3")
                    .addQueryParameter("genre_id_list", genreIds.joinToString(","))
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
        val mangaTitle = resolve(titleDetailsObject["title_name"]!!).jsonPrimitive.content
        val episodeIdRefs = resolve(titleDetailsObject["episode_id_list"]!!).jsonArray
        val episodeIds = episodeIdRefs.map { resolve(it).jsonPrimitive.content }

        val formBody = FormBody.Builder()
            .add("platform", "3")
            .add("episode_id_list", episodeIds.joinToString(","))
            .build()

        val params = (0 until formBody.size).associate { formBody.name(it) to formBody.value(it) }
        val hash = generateHash(params)

        val postHeaders = headersBuilder()
            .add("Origin", baseUrl)
            .add("x-bambi-is-crawler", "false")
            .add("x-bambi-hash", hash)
            .build()

        val apiRequest = POST("$apiUrl/episode/list", postHeaders, formBody)
        val apiResponse = client.newCall(apiRequest).execute()

        if (!apiResponse.isSuccessful) {
            throw Exception("API request failed with code ${apiResponse.code}: ${apiResponse.body.string()}")
        }

        val result = apiResponse.parseAs<EpisodeListResponse>()

        return result.episodeList.map { chapter ->
            SChapter.create().apply {
                val paddedId = chapter.titleId.toString().padStart(5, '0')
                url = "/comics/title/$paddedId/episode/${chapter.episodeId}"

                val originalChapterName = chapter.episodeName.trim()
                val chapterName = if (originalChapterName.startsWith(mangaTitle)) {
                    "【第${chapter.index}話】 $originalChapterName" // If entry title is in chapter name, that part of the chapter name is missing, so index is added here to the name
                } else {
                    originalChapterName
                }

                // It is possible to read paid chapters even though you have to purchase them on the website, so leaving this here in case they change it
                /*
                name = if (chapter.point > 0 && chapter.badge != 3 && chapter.rentalFinishTime == null) {
                    "🔒 $chapterName"
                } else {
                    chapterName
                }
                 */

                name = chapterName
                chapter_number = chapter.index.toFloat()
                date_upload = dateFormat.tryParse(chapter.startTime)
            }
        }.reversed()
    }

    // Pages
    /*
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservable()
            .map { response ->
                if (!response.isSuccessful) {
                    if (response.code == 400) {
                        throw Exception("This chapter is locked. Log in via WebView and rent or purchase this chapter to read.")
                    }
                    throw Exception("HTTP error ${response.code}")
                }
                pageListParse(response)
            }
    }
     */

    override fun pageListParse(response: Response): List<Page> {
        val apiResponse = response.parseAs<ViewerApiResponse>()
        val seed = apiResponse.scrambleSeed
        val ver = apiResponse.scrambleVer
        val fragment = if (ver == 2) "scramble_seed_v2" else "scramble_seed"
        return apiResponse.pageList.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = "$imageUrl#$fragment=$seed")
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val episodeId = chapter.url.substringAfter("episode/")
        val url = "$apiUrl/web/episode/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "3")
            .addQueryParameter("episode_id", episodeId)
            .build()
        return hashedGet(url)
    }

    private fun generateHash(params: Map<String, String>): String {
        val paramStrings = params.toSortedMap().map { (key, value) ->
            getHashedParam(key, value)
        }
        val joinedParams = paramStrings.joinToString(",")
        val hash1 = joinedParams.encodeUtf8().sha256().hex()
        return hash1.encodeUtf8().sha512().hex()
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
            .add("x-bambi-hash", hash)
            .build()
        return GET(url, newHeaders)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Search query will ignore genre filter"),
        GenreFilter(getGenreList()),
    )

    private class GenreFilter(private val genres: Array<Pair<String, String>>) :
        Filter.Select<String>("Filter by", genres.map { it.first }.toTypedArray()) {
        fun toUriPart() = genres[state].second
    }

    private fun getGenreList() = arrayOf(
        Pair("(ランキング) まんが総合", "/ranking/1"),
        Pair("(ランキング) 急上昇", "/ranking/2"),
        Pair("(ランキング) 読切", "/ranking/3"),
        Pair("(ランキング) ラブ", "/ranking/4"),
        Pair("(ランキング) ホラー・ミステリー", "/ranking/5"),
        Pair("(ランキング) ファンタジー", "/ranking/6"),
        Pair("(ランキング) ギャグ・エッセイ", "/ranking/7"),
        Pair("ギャグ・エッセイ", "/genre/1"),
        Pair("ラブ", "/genre/2"),
        Pair("ホラー・ミステリー", "/genre/3"),
        Pair("家族", "/genre/4"),
        Pair("青春・学園", "/genre/5"),
        Pair("友情", "/genre/6"),
        Pair("ファンタジー", "/genre/7"),
        Pair("ドリーム・サクセス", "/genre/8"),
        Pair("異世界", "/genre/9"),
    )

    // Unsupported
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
