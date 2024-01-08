package eu.kanade.tachiyomi.extension.zh.kuaikanmanhua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class Kuaikanmanhua : HttpSource() {

    override val name = "快看漫画"

    override val id: Long = 8099870292642776005

    override val baseUrl = "https://www.kuaikanmanhua.com"

    override val lang = "zh"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val apiUrl = "https://api.kkmh.com"

    private val json: Json by injectLazy()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/v1/topic_new/lists/get_by_tag?tag=0&since=${(page - 1) * 10}", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body.string()
        val jsonList = json.parseToJsonElement(body).jsonObject["data"]!!
            .jsonObject["topics"]!!
            .jsonArray
        return parseMangaJsonArray(jsonList)
    }

    private fun parseMangaJsonArray(jsonList: JsonArray, isSearch: Boolean = false): MangasPage {
        val mangaList = jsonList.map {
            val mangaObj = it.jsonObject

            SManga.create().apply {
                title = mangaObj["title"]!!.jsonPrimitive.content
                thumbnail_url = mangaObj["vertical_image_url"]!!.jsonPrimitive.content
                url = "/web/topic/" + mangaObj["id"]!!.jsonPrimitive.int
            }
        }

        // KKMH does not have pages when you search
        return MangasPage(mangaList, hasNextPage = mangaList.size > 9 && !isSearch)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/v1/topic_new/lists/get_by_tag?tag=19&since=${(page - 1) * 10}", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(TOPIC_ID_SEARCH_PREFIX)) {
            val newQuery = query.removePrefix(TOPIC_ID_SEARCH_PREFIX)
            return client.newCall(GET("$apiUrl/v1/topics/$newQuery"))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    details.url = "/web/topic/$newQuery"
                    MangasPage(listOf(details), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty()) {
            GET("$apiUrl/v1/search/topic?q=$query&size=18", headers)
        } else {
            lateinit var genre: String
            lateinit var status: String
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        genre = filter.toUriPart()
                    }
                    is StatusFilter -> {
                        status = filter.toUriPart()
                    }
                    else -> {}
                }
            }
            GET("$apiUrl/v1/search/by_tag?since=${(page - 1) * 10}&tag=$genre&sort=1&query_category=%7B%22update_status%22:$status%7D")
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body.string()
        val jsonObj = json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject
        if (jsonObj["hit"] != null) {
            return parseMangaJsonArray(jsonObj["hit"]!!.jsonArray, true)
        }

        return parseMangaJsonArray(jsonObj["topics"]!!.jsonArray, false)
    }

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        // Convert the stored url to one that works with the api
        val newUrl = apiUrl + "/v1/topics/" + manga.url.trimEnd('/').substringAfterLast("/")
        val response = client.newCall(GET(newUrl)).execute()
        val sManga = mangaDetailsParse(response).apply { initialized = true }
        return Observable.just(sManga)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val data = json.parseToJsonElement(response.body.string())
            .jsonObject["data"]!!
            .jsonObject

        title = data["title"]!!.jsonPrimitive.content
        thumbnail_url = data["vertical_image_url"]!!.jsonPrimitive.content
        author = data["user"]!!.jsonObject["nickname"]!!.jsonPrimitive.content
        description = data["description"]!!.jsonPrimitive.content
        status = data["update_status_code"]!!.jsonPrimitive.int
    }

    // Chapters & Pages

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val newUrl = apiUrl + "/v1/topics/" + manga.url.trimEnd('/').substringAfterLast("/")
        val response = client.newCall(GET(newUrl)).execute()
        val chapters = chapterListParse(response)
        return Observable.just(chapters)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = json.parseToJsonElement(response.body.string())
            .jsonObject["data"]!!
            .jsonObject
        val chaptersJson = data["comics"]!!.jsonArray
        val chapters = mutableListOf<SChapter>()

        for (i in 0 until chaptersJson.size) {
            val obj = chaptersJson[i].jsonObject
            chapters.add(
                SChapter.create().apply {
                    url = "/web/comic/" + obj["id"]!!.jsonPrimitive.content
                    name = obj["title"]!!.jsonPrimitive.content +
                        if (!obj["can_view"]!!.jsonPrimitive.boolean) {
                            " \uD83D\uDD12"
                        } else {
                            ""
                        }
                    date_upload = obj["created_at"]!!.jsonPrimitive.long * 1000
                },
            )
        }
        return chapters
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val request = client.newCall(pageListRequest(chapter)).execute()
        return Observable.just(pageListParse(request))
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // if (chapter.name.endsWith("🔒")) {
        //    throw Exception("[此章节为付费内容]")
        // }
        return GET(baseUrl + chapter.url)
    }

    private val fixJson: (MatchResult) -> CharSequence = {
            match: MatchResult ->
        val str = match.value
        val out = str[0] + "\"" + str.subSequence(1, str.length - 1) + "\"" + str[str.length - 1]
        out
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(comicImages)")!!.data()
        val images = script.substringAfter("comicImages:")
            .substringBefore(",is_vip_exclusive")
            .replace("""(:([^\[\{\"]+?)[\},])""".toRegex(), fixJson)
            .replace("""([,{]([^\[\{\"]+?)[\}:])""".toRegex(), fixJson)
            .let { json.parseToJsonElement(it).jsonArray }
        val variable = script.substringAfter("(function(")
            .substringBefore("){")
            .split(",")
        val value = script.substringAfterLast("}}(")
            .substringBefore("));")
            .split(",")

        return images.mapIndexed { index, jsonEl ->
            val urlVar = jsonEl.jsonObject["url"]!!.jsonPrimitive.content
            val imageUrl = value[variable.indexOf(urlVar)]
                .replace("\\u002F", "/")
                .replace("\"", "")

            Page(index, "", imageUrl)
        }
    }

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("注意：不影響按標題搜索"),
        StatusFilter(),
        GenreFilter(),
    )

    override fun imageUrlParse(response: Response): String {
        throw Exception("Not used")
    }

    private class GenreFilter : UriPartFilter(
        "题材",
        arrayOf(
            Pair("全部", "0"),
            Pair("恋爱", "20"),
            Pair("古风", "46"),
            Pair("校园", "47"),
            Pair("奇幻", "22"),
            Pair("大女主", "77"),
            Pair("治愈", "27"),
            Pair("总裁", "52"),
            Pair("完结", "40"),
            Pair("唯美", "58"),
            Pair("日漫", "57"),
            Pair("韩漫", "60"),
            Pair("穿越", "80"),
            Pair("正能量", "54"),
            Pair("灵异", "32"),
            Pair("爆笑", "24"),
            Pair("都市", "48"),
            Pair("萌系", "62"),
            Pair("玄幻", "63"),
            Pair("日常", "19"),
            Pair("投稿", "76"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "类别",
        arrayOf(
            Pair("全部", "1"),
            Pair("连载中", "2"),
            Pair("已完结", "3"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        const val TOPIC_ID_SEARCH_PREFIX = "topic:"
    }
}
