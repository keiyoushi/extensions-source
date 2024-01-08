package eu.kanade.tachiyomi.multisrc.webtoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

open class WebtoonsTranslate(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val translateLangCode: String,
) : Webtoons(name, baseUrl, lang) {

    // popularMangaRequest already returns manga sorted by latest update
    override val supportsLatest = false

    private val apiBaseUrl = "https://global.apis.naver.com".toHttpUrlOrNull()!!
    private val mobileBaseUrl = "https://m.webtoons.com".toHttpUrlOrNull()!!
    private val thumbnailBaseUrl = "https://mwebtoon-phinf.pstatic.net"

    private val pageSize = 24

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .removeAll("Referer")
        .add("Referer", mobileBaseUrl.toString())

    private fun mangaRequest(page: Int, requeztSize: Int): Request {
        val url = apiBaseUrl
            .resolve("/lineWebtoon/ctrans/translatedWebtoons_jsonp.json")!!
            .newBuilder()
            .addQueryParameter("orderType", "UPDATE")
            .addQueryParameter("offset", "${(page - 1) * requeztSize}")
            .addQueryParameter("size", "$requeztSize")
            .addQueryParameter("languageCode", translateLangCode)
            .build()
        return GET(url.toString(), headers)
    }

    // Webtoons translations doesn't really have a "popular" sort; just "UPDATE", "TITLE_ASC",
    // and "TITLE_DESC".  Pick UPDATE as the most useful sort.
    override fun popularMangaRequest(page: Int): Request = mangaRequest(page, pageSize)

    override fun popularMangaParse(response: Response): MangasPage {
        val offset = response.request.url.queryParameter("offset")!!.toInt()
        val result = json.parseToJsonElement(response.body.string()).jsonObject
        val responseCode = result["code"]!!.jsonPrimitive.content

        if (responseCode != "000") {
            throw Exception("Error getting popular manga: error code $responseCode")
        }

        val titles = result["result"]!!.jsonObject
        val totalCount = titles["totalCount"]!!.jsonPrimitive.int

        val mangaList = titles["titleList"]!!.jsonArray
            .map { mangaFromJson(it.jsonObject) }

        return MangasPage(mangaList, hasNextPage = totalCount > pageSize + offset)
    }

    private fun mangaFromJson(manga: JsonObject): SManga {
        val relativeThumnailURL = manga["thumbnailIPadUrl"]?.jsonPrimitive?.contentOrNull
            ?: manga["thumbnailMobileUrl"]?.jsonPrimitive?.contentOrNull

        return SManga.create().apply {
            title = manga["representTitle"]!!.jsonPrimitive.content
            author = manga["writeAuthorName"]!!.jsonPrimitive.content
            artist = manga["pictureAuthorName"]?.jsonPrimitive?.contentOrNull ?: author
            thumbnail_url = if (relativeThumnailURL != null) "$thumbnailBaseUrl$relativeThumnailURL" else null
            status = SManga.UNKNOWN
            url = mobileBaseUrl
                .resolve("/translate/episodeList")!!
                .newBuilder()
                .addQueryParameter("titleNo", manga["titleNo"]!!.jsonPrimitive.int.toString())
                .addQueryParameter("languageCode", translateLangCode)
                .addQueryParameter("teamVersion", (manga["teamVersion"]?.jsonPrimitive?.intOrNull ?: 0).toString())
                .build()
                .toString()
        }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    /**
     * Don't see a search function for Fan Translations, so let's do it client side.
     * There's 75 webtoons as of 2019/11/21, a hardcoded request of 200 should be a sufficient request
     * to get all titles, in 1 request, for quite a while
     */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = mangaRequest(page, 200)

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val result = json.parseToJsonElement(response.body.string()).jsonObject
        val responseCode = result["code"]!!.jsonPrimitive.content

        if (responseCode != "000") {
            throw Exception("Error getting manga: error code $responseCode")
        }

        val mangaList = result["result"]!!.jsonObject["titleList"]!!.jsonArray
            .map { mangaFromJson(it.jsonObject) }
            .filter { it.title.contains(query, ignoreCase = true) }

        return MangasPage(mangaList, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val getMetaProp = fun(property: String): String =
            document.head().select("meta[property=\"$property\"]").attr("content")
        var parsedAuthor = getMetaProp("com-linewebtoon:webtoon:author")
        var parsedArtist = parsedAuthor
        val authorSplit = parsedAuthor.split(" / ", limit = 2)
        if (authorSplit.count() > 1) {
            parsedAuthor = authorSplit[0]
            parsedArtist = authorSplit[1]
        }

        return SManga.create().apply {
            title = getMetaProp("og:title")
            artist = parsedArtist
            author = parsedAuthor
            description = getMetaProp("og:description")
            status = SManga.UNKNOWN
            thumbnail_url = getMetaProp("og:image")
        }
    }

    override fun chapterListSelector(): String = throw Exception("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not used")

    override fun chapterListRequest(manga: SManga): Request {
        val mangaUrl = manga.url.toHttpUrlOrNull()!!
        val titleNo = mangaUrl.queryParameter("titleNo")
        val teamVersion = mangaUrl.queryParameter("teamVersion")
        val chapterListUrl = apiBaseUrl
            .resolve("/lineWebtoon/ctrans/translatedEpisodes_jsonp.json")!!
            .newBuilder()
            .addQueryParameter("titleNo", titleNo)
            .addQueryParameter("languageCode", translateLangCode)
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "10000")
            .addQueryParameter("teamVersion", teamVersion)
            .toString()
        return GET(chapterListUrl, mobileHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.parseToJsonElement(response.body.string()).jsonObject
        val responseCode = result["code"]!!.jsonPrimitive.content

        if (responseCode != "000") {
            val message = result["message"]?.jsonPrimitive?.content ?: "error code $responseCode"
            throw Exception("Error getting chapter list: $message")
        }

        return result["result"]!!.jsonObject["episodes"]!!.jsonArray
            .filter { it.jsonObject["translateCompleted"]!!.jsonPrimitive.boolean }
            .map { parseChapterJson(it.jsonObject) }
            .reversed()
    }

    private fun parseChapterJson(obj: JsonObject): SChapter = SChapter.create().apply {
        name = obj["title"]!!.jsonPrimitive.content + " #" + obj["episodeSeq"]!!.jsonPrimitive.int
        chapter_number = obj["episodeSeq"]!!.jsonPrimitive.int.toFloat()
        date_upload = obj["updateYmdt"]!!.jsonPrimitive.long
        scanlator = obj["teamVersion"]!!.jsonPrimitive.int.takeIf { it != 0 }?.toString() ?: "(wiki)"

        val chapterUrl = apiBaseUrl
            .resolve("/lineWebtoon/ctrans/translatedEpisodeDetail_jsonp.json")!!
            .newBuilder()
            .addQueryParameter("titleNo", obj["titleNo"]!!.jsonPrimitive.int.toString())
            .addQueryParameter("episodeNo", obj["episodeNo"]!!.jsonPrimitive.int.toString())
            .addQueryParameter("languageCode", obj["languageCode"]!!.jsonPrimitive.content)
            .addQueryParameter("teamVersion", obj["teamVersion"]!!.jsonPrimitive.int.toString())
            .toString()

        setUrlWithoutDomain(chapterUrl)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(apiBaseUrl.resolve(chapter.url).toString(), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.parseToJsonElement(response.body.string()).jsonObject

        return result["result"]!!.jsonObject["imageInfo"]!!.jsonArray
            .mapIndexed { i, jsonEl ->
                Page(i, "", jsonEl.jsonObject["imageUrl"]!!.jsonPrimitive.content)
            }
    }

    override fun getFilterList(): FilterList = FilterList()
}
