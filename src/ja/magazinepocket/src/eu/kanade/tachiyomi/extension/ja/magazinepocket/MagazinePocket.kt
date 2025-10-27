package eu.kanade.tachiyomi.extension.ja.magazinepocket

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
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
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class MagazinePocket : HttpSource() {
    override val name = "Magazine Pocket"
    override val baseUrl = "https://pocket.shonenmagazine.com"
    override val lang = "ja"
    override val supportsLatest = true
    override val versionId = 2

    private val apiUrl = "https://api.pocket.shonenmagazine.com"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)
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
                url = "/title/$paddedId"
                title = manga.titleName
                thumbnail_url = manga.thumbnailImageUrl
            }
        }
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val calendar = GregorianCalendar(TimeZone.getTimeZone("Asia/Tokyo")).apply {
            time = Date()
            add(Calendar.DAY_OF_MONTH, -(page - 1))
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
            .addQueryParameter("platform", "3")
            .build()
        return hashedGet(url)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<LatestTitleListResponse>()
        val manga = result.titleList.map { manga ->
            SManga.create().apply {
                val paddedId = manga.titleId.toString().padStart(5, '0')
                url = "/title/$paddedId"
                title = manga.titleName
                thumbnail_url = manga.thumbnailImageUrl
            }
        }
        return MangasPage(manga, true)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = apiUrl.toHttpUrl().newBuilder()
                .addPathSegments("web/search/title")
                .addQueryParameter("keyword", query)
                .addQueryParameter("limit", "99999")
                .addQueryParameter("platform", "3")
                .build()
            return hashedGet(url)
        }

        val genreFilter = filters.firstInstance<GenreFilter>()
        val uriPart = genreFilter.toUriPart()
        if (uriPart.startsWith("/search/genre/")) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments(uriPart.removePrefix("/"))
                .build()
            return GET(url, headers)
        }

        val rankingId = uriPart.substringAfter("/ranking/")
        val offset = (page - 1) * pageLimit
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments("ranking/all")
            .addQueryParameter("platform", "3")
            .addQueryParameter("ranking_id", rankingId)
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "26")
            .build()
        return hashedGet(url)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestUrl = response.request.url.toString()
        if (requestUrl.contains("/web/search/")) {
            val result = response.parseAs<SearchApiResponse>()
            val mangas = result.searchTitleList.map { manga ->
                SManga.create().apply {
                    val paddedId = manga.titleId.toString().padStart(5, '0')
                    url = "/title/$paddedId"
                    title = manga.titleName
                    thumbnail_url = manga.bannerImageUrl
                }
            }
            return MangasPage(mangas.reversed(), false)
        }

        if (response.request.url.toString().contains("/search/genre/")) {
            val document = response.asJsoup()
            val nuxtData = document.selectFirst("script#__NUXT_DATA__")?.data()
                ?: return MangasPage(emptyList(), false)

            val rootArray = nuxtData.parseAs<JsonArray>()
            fun resolve(ref: JsonElement): JsonElement = rootArray[ref.jsonPrimitive.content.toInt()]

            val genreResultObject = rootArray.firstOrNull { it is JsonObject && "search_title_list" in it.jsonObject }
                ?: return MangasPage(emptyList(), false)

            val mangaRefs = resolve(genreResultObject.jsonObject["search_title_list"]!!).jsonArray

            val mangas = mangaRefs.map { ref ->
                val mangaObject = resolve(ref).jsonObject
                val id = resolve(mangaObject["title_id"]!!).jsonPrimitive
                SManga.create().apply {
                    val paddedId = id.toString().padStart(5, '0')
                    url = "/title/$paddedId"
                    title = resolve(mangaObject["title_name"]!!).jsonPrimitive.content
                    thumbnail_url = mangaObject["banner_image_url"]?.let { resolve(it).jsonPrimitive.content }
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
            .findLast { "title_name" in it && "author_text" in it && "introduction_text" in it && "genre_id_list" in it }
            ?.jsonObject

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
            title = resolve(titleDetailsObject?.get("title_name")!!).jsonPrimitive.content
            author = resolve(titleDetailsObject["author_text"]!!).jsonPrimitive.content
            description = resolve(titleDetailsObject["introduction_text"]!!).jsonPrimitive.content
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

        val formBody = FormBody.Builder()
            .add("platform", "3")
            .add("episode_id_list", episodeIds.joinToString(","))
            .build()

        val params = (0 until formBody.size).associate { formBody.name(it) to formBody.value(it) }
        val hash = generateHash(params)

        val postHeaders = headersBuilder()
            .add("Origin", baseUrl)
            .add("x-manga-is-crawler", "false")
            .add("x-manga-hash", hash)
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
                url = "/title/$paddedId/episode/${chapter.episodeId}"
                name = if (chapter.point > 0 && chapter.badge != 3 && chapter.rentalFinishTime == null) {
                    "🔒 ${chapter.episodeName}"
                } else {
                    chapter.episodeName
                }
                date_upload = dateFormat.tryParse(chapter.startTime)
            }
        }.reversed()
    }

    // Pages
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

    override fun pageListParse(response: Response): List<Page> {
        val apiResponse = response.parseAs<ViewerApiResponse>()
        val seed = apiResponse.scrambleSeed
        return apiResponse.pageList.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = "$imageUrl#scramble_seed=$seed")
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

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Search query will ignore genre filter"),
        GenreFilter(getGenreList()),
    )

    private class GenreFilter(private val genres: Array<Pair<String, String>>) :
        Filter.Select<String>("Filter by", genres.map { it.first }.toTypedArray()) {
        fun toUriPart() = genres[state].second
    }

    private fun getGenreList() = arrayOf(
        Pair("(ランキング) すべて", "/ranking/30"),
        Pair("(ランキング) まずはコレ！", "/ranking/2"),
        Pair("(ランキング) オリジナル", "/ranking/1"),
        Pair("(ランキング) 新作", "/ranking/31"),
        Pair("(ランキング) アクション", "/ranking/21"),
        Pair("(ランキング) スポーツ", "/ranking/22"),
        Pair("(ランキング) 恋愛", "/ranking/23"),
        Pair("(ランキング) 異世界", "/ranking/24"),
        Pair("(ランキング) サスペンス", "/ranking/25"),
        Pair("(ランキング) 裏社会・ヤンキー", "/ranking/26"),
        Pair("(ランキング) ドラマ", "/ranking/27"),
        Pair("(ランキング) ファンタジー", "/ranking/28"),
        Pair("(ランキング) 日常", "/ranking/29"),
        Pair("恋愛・ラブコメ", "/search/genre/1"),
        Pair("ホラー・ミステリー・サスペンス", "/search/genre/2"),
        Pair("ギャグ・コメディー・日常", "/search/genre/3"),
        Pair("SF・ファンタジー", "/search/genre/4"),
        Pair("スポーツ", "/search/genre/5"),
        Pair("ヒューマンドラマ", "/search/genre/6"),
        Pair("裏社会・アングラ・ヤンキー", "/search/genre/7"),
        Pair("アクション・バトル", "/search/genre/8"),
        Pair("異世界・異能力", "/search/genre/9"),
        Pair("読切", "/search/genre/10"),
        Pair("MGP", "/search/genre/11"),
        Pair("第98回新人漫画賞", "/search/genre/12"),
        Pair("第99回新人漫画賞", "/search/genre/13"),
        Pair("第100回新人漫画賞", "/search/genre/14"),
        Pair("第101回新人漫画賞", "/search/genre/15"),
        Pair("第102回新人漫画賞", "/search/genre/16"),
        Pair("第103回新人漫画賞", "/search/genre/17"),
        Pair("第104回新人漫画賞", "/search/genre/18"),
        Pair("第105回新人漫画賞", "/search/genre/19"),
        Pair("第106回新人漫画賞", "/search/genre/20"),
        Pair("第107回新人漫画賞", "/search/genre/21"),
        Pair("第108回新人漫画賞", "/search/genre/22"),
        Pair("第109回新人漫画賞", "/search/genre/23"),
        Pair("第110回新人漫画賞", "/search/genre/24"),
        Pair("2023真夏の読み切り15連弾", "/search/genre/25"),
        Pair("マガジンライズ", "/search/genre/26"),
        Pair("第111回新人漫画賞", "/search/genre/27"),
        Pair("少女／女性", "/search/genre/28"),
        Pair("新人漫画大賞", "/search/genre/29"),
        Pair("第75回新人漫画賞", "/search/genre/30"),
        Pair("第79回新人漫画賞", "/search/genre/31"),
        Pair("第85回新人漫画賞", "/search/genre/32"),
        Pair("第88回新人漫画賞", "/search/genre/33"),
        Pair("第89回新人漫画賞", "/search/genre/34"),
        Pair("第91回新人漫画賞", "/search/genre/35"),
        Pair("第92回新人漫画賞", "/search/genre/36"),
        Pair("第94回新人漫画賞", "/search/genre/37"),
        Pair("第95回新人漫画賞", "/search/genre/38"),
        Pair("第96回新人漫画賞", "/search/genre/39"),
        Pair("第97回新人漫画賞", "/search/genre/40"),
        Pair("第112回新人漫画賞", "/search/genre/41"),
        Pair("第113回新人漫画大賞", "/search/genre/42"),
        Pair("サッカー", "/search/genre/43"),
        Pair("テニス", "/search/genre/44"),
        Pair("バスケ", "/search/genre/45"),
        Pair("格闘技", "/search/genre/46"),
        Pair("野球", "/search/genre/47"),
        Pair("女性向け異世界", "/search/genre/48"),
        Pair("アニメ化", "/search/genre/49"),
        Pair("実写化", "/search/genre/50"),
        Pair("車・バイク", "/search/genre/51"),
        Pair("グルメ・料理", "/search/genre/52"),
        Pair("医療", "/search/genre/53"),
        Pair("頭脳戦", "/search/genre/54"),
        Pair("サバイバル", "/search/genre/55"),
        Pair("復讐劇", "/search/genre/56"),
        Pair("70～80年代", "/search/genre/57"),
        Pair("90年代", "/search/genre/58"),
        Pair("金田一シリーズ", "/search/genre/59"),
        Pair("第114回新人漫画大賞", "/search/genre/60"),
        Pair("連載獲得ダービー", "/search/genre/61"),
        Pair("有名漫画賞", "/search/genre/62"),
        Pair("探偵・警察", "/search/genre/63"),
        Pair("歴史・時代", "/search/genre/64"),
        Pair("不倫・浮気", "/search/genre/65"),
        Pair("犬・猫", "/search/genre/66"),
    )

    // Unsupported
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
