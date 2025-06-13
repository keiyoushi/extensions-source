package eu.kanade.tachiyomi.extension.zh.rumanhua

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
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
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException

class Rumanhua : HttpSource(), ConfigurableSource {
    override val lang: String = "zh"
    override val name: String = "如漫画"
    override val supportsLatest = true

    private val preferences: SharedPreferences = getPreferences()

    override val baseUrl: String = getTargetUrl()

    override val client: OkHttpClient = network.cloudflareClient

    private fun getTargetUrl(): String {
        val defaultUrl = "http://www.rumanhua1.com"
        val url = preferences.getString(APP_CUSTOMIZATION_URL, defaultUrl)!!
        if (url.isNotBlank()) {
            return url
        }
        return defaultUrl
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val lis = mutableListOf<SChapter>()
        document.select("div.forminfo > div.chapterList > div.chapterlistload > ul > a")
            .forEach { element ->
                lis.add(
                    SChapter.create().apply {
                        name = element.text()
                        setUrlWithoutDomain(element.absUrl("href"))
                    },
                )
            }

        // get more chapter ...
        val bid = response.request.url.pathSegments[0]
        val body = FormBody.Builder().add("id", bid).build()
        val moreRequest = POST("$baseUrl/morechapter", headers, body)
        val moreResponse = client.newCall(moreRequest).execute()
        if (!moreResponse.isSuccessful) {
            throw IOException("Request failed: ${moreRequest.url}")
        }

        val moreChapter = moreResponse.parseAs<MoreChapter>()
        if (moreChapter.code == "200") {
            jsonInstance.decodeFromJsonElement<List<MoreChapterInfo>>(moreChapter.data).forEach {
                lis.add(
                    SChapter.create().apply {
                        name = it.chaptername
                        url = "/$bid/${it.chapterid}.html"
                    },
                )
            }
        }

        return lis
    }

    @Serializable
    private class MoreChapter(
        val code: String,
        val msg: String,
        val data: JsonElement,
    )

    @Serializable
    private class MoreChapterInfo(
        val chapterid: String,
        val chaptername: String,
    )

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Latest

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/rank/5", headers)

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        val info = document.selectFirst("div.forminfo > div.comicInfo")!!

        return SManga.create().apply {
            info.select("div.detinfo > p").forEach { element ->
                when (element.attr("class")) {
                    "gray" -> {
                        element.select("span").forEach { span ->
                            val spanText = span.text()
                            val dgenre =
                                removePrefixAndCheck(spanText, "标 签：")?.replace(" ", ", ")
                            if (dgenre != null) {
                                genre = dgenre
                            } else {
                                status = when (removePrefixAndCheck(spanText, "状 态：")) {
                                    "连载中" -> SManga.ONGOING
                                    "已完结" -> SManga.COMPLETED
                                    else -> SManga.UNKNOWN
                                }
                            }
                        }
                    }

                    "content" -> {
                        description = element.text()
                    }

                    else -> {
                        element.select("span").forEach { span ->
                            val dauthor = removePrefixAndCheck(span.text(), "作 者：")
                            if (dauthor != null) {
                                author = dauthor
                            }
                        }
                    }
                }
            }
            title = info.selectFirst("div.detinfo > h1")!!.text()
            thumbnail_url = document.selectFirst("div.mhcover > div.himg > img")?.absUrl("data-src")
        }
    }

    private fun removePrefixAndCheck(input: String, prefix: String): String? {
        if (input.isEmpty() || prefix.isEmpty()) {
            return null
        }
        if (input.startsWith(prefix)) {
            return input.substring(prefix.length).trim()
        }
        return null
    }

    // Pages

    private val pageDecrypt = PageDecrypt()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val lis = mutableListOf<Page>()
        pageDecrypt.toDecrypt(document).parseAs<List<String>>().forEachIndexed { index, img ->
            lis.add(Page(index, imageUrl = img))
        }

        return lis
    }

    // Popular

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val lis = mutableListOf<SManga>()
        document.select("div.float-r.rs-p > div.wholike > div.likedata").forEach { element ->
            lis.add(
                SManga.create().apply {
                    title = element.selectFirst("div.likeinfo > a")!!.text()
                    setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                    thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
                },
            )
        }

        return MangasPage(lis, false)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/rank/1", headers)

    // Search

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.encodedPath != "/s") {
            return popularMangaParse(response)
        }

        val document = response.asJsoup()

        val lis = mutableListOf<SManga>()
        document.select("div.item-data.s-data > div.col-auto").forEach { element ->
            lis.add(
                SManga.create().apply {
                    title = element.selectFirst("a > p.e-title")!!.text()
                    setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                    thumbnail_url =
                        element.selectFirst("a > div.edit-top > img")?.absUrl("data-src")
                },
            )
        }

        return MangasPage(lis, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()

        if (query != "" && !query.contains("-")) {
            val body = FormBody.Builder().add("k", query.take(12)).build()
            return POST(
                urlBuilder.encodedPath("/s").build().toString(),
                headers,
                body,
            )
        } else {
            // RankGroup or CategoryGroup take one and reset the other
            var url: String? = null
            for (filter in filters.filterIsInstance<UriPartFilter>()) {
                if (url != null) {
                    filter.reset()
                } else {
                    val path = filter.toUriPart()
                    if (path != "") {
                        url = path
                    }
                }
            }
            if (url != null) {
                return GET(urlBuilder.encodedPath(url).build().toString(), headers)
            }
            throw IOException("Invalid filter types")
        }
    }

    // Filter

    override fun getFilterList() = FilterList(
        RankGroup(),
        CategoryGroup(),
    )

    private class RankGroup : UriPartFilter(
        "排行榜",
        arrayOf(
            Pair("None", ""),
            Pair("精品榜", "/rank/1"),
            Pair("人气榜", "/rank/2"),
            Pair("推荐榜", "/rank/3"),
            Pair("黑马榜", "/rank/4"),
            Pair("最近更新", "/rank/5"),
            Pair("新漫画", "/rank/6"),
        ),
    )

    private class CategoryGroup : UriPartFilter(
        "按类型",
        arrayOf(
            Pair("None", ""),
            Pair("冒险", "/sort/1"),
            Pair("热血", "/sort/2"),
            Pair("都市", "/sort/3"),
            Pair("玄幻", "/sort/4"),
            Pair("悬疑", "/sort/5"),
            Pair("耽美", "/sort/6"),
            Pair("恋爱", "/sort/7"),
            Pair("生活", "/sort/8"),
            Pair("搞笑", "/sort/9"),
            Pair("穿越", "/sort/10"),
            Pair("修真", "/sort/11"),
            Pair("后宫", "/sort/12"),
            Pair("女主", "/sort/13"),
            Pair("古风", "/sort/14"),
            Pair("连载", "/sort/15"),
            Pair("完结", "/sort/16"),
        ),
    )

    private open class UriPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0,
    ) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
        open fun toUriPart() = vals[state].second
        open fun reset() {
            state = 0
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = APP_CUSTOMIZATION_URL
            title = "自定义url"
            summary = "修改后需要重启应用生效"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(APP_CUSTOMIZATION_URL, newValue as String).commit()
            }
        }.let(screen::addPreference)
    }
}

const val APP_CUSTOMIZATION_URL = "APP_CUSTOMIZATION_URL"
