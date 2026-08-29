package eu.kanade.tachiyomi.extension.ja.ciaoplus

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.encodeUtf8

@Source
abstract class CiaoPlus : HttpSource() {
    override val supportsLatest = true

    private val apiUrl = "https://api.ciao.shogakukan.co.jp"
    private val pageLimit = 25

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request = rankingRequest("1", page)

    override fun popularMangaParse(response: Response): MangasPage {
        val rankingResult = response.parseAs<RankingApiResponse>()
        val titleIds = rankingResult.rankingTitleList.map { it.id.toString().padStart(5, '0') }
        if (titleIds.isEmpty()) {
            return MangasPage(emptyList(), false)
        }

        val hasNextPage = titleIds.size > pageLimit
        val mangaIdsToFetch = if (hasNextPage) titleIds.dropLast(1) else titleIds
        val detailsUrl = "$apiUrl/title/list".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "3")
            .addQueryParameter("title_id_list", mangaIdsToFetch.joinToString(","))
            .build()

        val detailsResponse = client.newCall(hashedGet(detailsUrl)).execute()
        val mangas = detailsResponse.parseAs<TitleListResponse>().titleList.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/title/weekly".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "3")
            .build()
        return hashedGet(url)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<LatestResponse>()
        val todayIdList = result.weeklyList.first { it.weekdayIndex == result.todayWeekdayIndex }.titleIdList
        val titleById = result.titleList.associateBy { it.titleId }
        val mangas = todayIdList.mapNotNull { titleById[it]?.toSManga() }
        return MangasPage(mangas, false)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search/title".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("limit", "99999")
                .addQueryParameter("platform", "3")
                .build()
            return hashedGet(url)
        }

        val filter = filters.firstInstance<CategoryFilter>()
        return when (filter.type) {
            FilterType.GENRE -> {
                val url = "$apiUrl/search/title".toHttpUrl().newBuilder()
                    .addQueryParameter("platform", "3")
                    .addQueryParameter("genre_id", filter.id)
                    .addQueryParameter("limit", "99999")
                    .build()
                hashedGet(url)
            }
            FilterType.RANKING -> rankingRequest(filter.id, page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.first() == "search") {
            val mangas = response.parseAs<TitleListResponse>().titleList.map { it.toSManga() }
            return MangasPage(mangas, false)
        }
        return popularMangaParse(response)
    }

    private fun rankingRequest(rankingId: String, page: Int): Request {
        val offset = (page - 1) * pageLimit
        val url = "$apiUrl/ranking/all".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "3")
            .addQueryParameter("ranking_id", rankingId)
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "51")
            .addQueryParameter("is_top", "0")
            .build()
        return hashedGet(url)
    }

    // Details
    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        val titleId = manga.url.substringAfter("/title/")
        val url = "$apiUrl/title/list".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "3")
            .addQueryParameter("title_id_list", titleId)
            .build()
        return hashedGet(url)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<DetailResponse>().webTitle.first()
        val genre = result.genreIdList?.takeIf { it.isNotEmpty() }?.let { fetchGenreNames(it) }
        return result.toSManga(genre)
    }

    private fun fetchGenreNames(genreIds: List<Int>): String? {
        val url = "$apiUrl/genre/list".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "3")
            .addQueryParameter("genre_id_list", genreIds.joinToString(","))
            .build()

        val genreResponse = client.newCall(hashedGet(url)).execute()
        return genreResponse.parseAs<GenreListResponse>().genreList?.joinToString { it.genreName }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val resultIds = response.parseAs<DetailResponse>().webTitle.first()
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
            .add("X-Bambi-Hash", hash)
            .build()

        val apiRequest = POST("$apiUrl/episode/list", postHeaders, formBody)
        val apiResponse = client.newCall(apiRequest).execute()
        val result = apiResponse.parseAs<EpisodeListResponse>()
        return result.episodeList.map { it.toSChapter(resultIds.titleName) }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val apiResponse = response.parseAs<ViewerApiResponse>()
        val seed = apiResponse.scrambleSeed
        val fragment = if (apiResponse.scrambleVer == 2) "scramble_seed_v2" else "scramble_seed"
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
            .add("X-Bambi-Hash", hash)
            .build()
        return GET(url, newHeaders)
    }

    override fun getFilterList() = FilterList(
        CategoryFilter(),
    )

    // Unsupported
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
