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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
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
        val mangas = detailsResult.titleList.map { it.toSManga() }

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
            .map { it.toSManga() }
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
            val result = response.parseAs<TitleListResponse>()
            val mangas = result.titleList.map { it.toSManga() }
            return MangasPage(mangas, false)
        }
        return popularMangaParse(response)
    }

    // Details
    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val titleId = manga.url.substringAfter("/title/")
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments("title/list")
            .addQueryParameter("platform", "3")
            .addQueryParameter("title_id_list", titleId)
            .build()
        return hashedGet(url)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val details = response.parseAs<DetailResponse>()
        val result = details.webTitle.first()
        return SManga.create().apply {
            title = result.titleName
            author = result.authorText
            description = result.introductionText
            if (result.genreIdList.isNotEmpty()) {
                val genreApiUrl = apiUrl.toHttpUrl().newBuilder()
                    .addPathSegments("genre/list")
                    .addQueryParameter("platform", "3")
                    .addQueryParameter("genre_id_list", result.genreIdList.joinToString(","))
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
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val details = response.parseAs<DetailResponse>()
        val resultIds = details.webTitle.first()
        val episodeIds = resultIds.episodeIdList.map { it.toString() }
        if (episodeIds.isEmpty()) {
            return emptyList()
        }

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

        return result.episodeList.map { it.toSChapter(resultIds.titleName, dateFormat) }.reversed()
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
