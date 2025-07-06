package eu.kanade.tachiyomi.extension.zh.manxiaosi

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okio.IOException

class ManXiaoSi : HttpSource(), ConfigurableSource {
    override val lang: String = "zh"
    override val name: String = "漫小肆"

    override val supportsLatest: Boolean = true

    private val preferences = getPreferences()

    private val redirectBaseUrl: String = "http://www.freexcomic.com"

    @Volatile
    override var baseUrl: String = preferences.getString(APP_MIRROR_URL, redirectBaseUrl)!!

    override val client = network.cloudflareClient.newBuilder().addInterceptor {
        val request = it.request()
        if (request.url.toString().startsWith(redirectBaseUrl) && request.url.encodedPath != "/") {
            throw IOException("请从设置中选取可用的镜像网址；如果为空请检查是否正常访问：$redirectBaseUrl")
        }
        it.proceed(it.request())
    }.build()

    private val urlRedirect = UrlRedirect(client, headers)

    private var isRedirected = false

    @Synchronized
    private fun getRedirectUrl(): String {
        if (!isRedirected) {
            try {
                val lis = urlRedirect.redirect(redirectBaseUrl)
                preferences.edit().putString(APP_MIRROR_URL_LIST, lis.toJsonString()).apply()
                isRedirected = true
            } catch (_: Exception) {
            }
        }

        val lis = preferences.getString(APP_MIRROR_URL_LIST, "[]")!!.parseAs<List<String>>()

        return preferences.getString(APP_MIRROR_URL, if (lis.isEmpty()) "" else lis[0])!!
    }

    // latestUpdates

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val lis = mutableListOf<SManga>()
        document.select("#update_30 > li > div > a").forEach {
            lis.add(
                SManga.create().apply {
                    url = it.attr("href")
                    title = it.attr("title")
                    thumbnail_url = "$baseUrl/static/upload$url/cover.jpg"
                },
            )
        }
        return MangasPage(lis, document.selectFirst("#nextPage") != null)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/update?page=$page", headers)
    }

    // mangaDetails

    override fun getMangaUrl(manga: SManga): String {
        return super.getMangaUrl(manga)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val bookUrl = response.request.url.encodedPath
        val document = response.asJsoup()
        return SManga.create().apply {
            url = bookUrl
            title = document.selectFirst("div.banner_detail_form > div.info > h1")!!.text()
            thumbnail_url = "$baseUrl/static/upload$url/cover.jpg"
            author =
                document.selectFirst("div.banner_detail_form > div.info > p.subtitle:nth-child(3)")!!
                    .text().removePrefix("作者：")
            description =
                document.selectFirst("div.banner_detail_form > div.info > p.content")!!.text()

            val genreList = mutableListOf<String>()
            document.select("div.banner_detail_form > div.info > p.tip > span").forEach { span ->
                when (span.ownText()) {
                    "状态：" -> {
                        status = when (span.selectFirst("span > span")?.text()?.trim()) {
                            "连载中" -> SManga.ONGOING
                            "已完结" -> SManga.COMPLETED
                            else -> SManga.UNKNOWN
                        }
                    }

                    "地区：", "标签：" -> {
                        span.select("a").forEach {
                            genreList.add(
                                it.text().trim(),
                            )
                        }
                    }

                    else -> {}
                }
            }
            genre = genreList.joinToString()
        }
    }

    // chapterList
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val lis = mutableListOf<SChapter>()
        document.select("#detail-list-select > li > a").forEach {
            lis.add(
                SChapter.create().apply {
                    url = it.attr("href")
                    name = it.text()
                },
            )
        }
        return lis
    }

    // image

    override fun imageRequest(page: Page): Request {
        return GET("$baseUrl${page.imageUrl}", headers)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // pageList

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val lis = mutableListOf<Page>()
        document.select("#content > div.comiclist > div.comicpage > div > img")
            .forEachIndexed { index, it ->
                val url = it.attr("data-original")
                lis.add(Page(index, imageUrl = url.toHttpUrl().encodedPath))
            }
        return lis
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val lis = mutableListOf<SManga>()
        document.select("div.clearfix > div > div > h2 > a , div.mh-itme-top > a ").forEach {
            lis.add(
                SManga.create().apply {
                    url = it.attr("href")
                    title = it.attr("title")
                    thumbnail_url = "$baseUrl/static/upload$url/cover.jpg"
                },
            )
        }
        return MangasPage(lis, false)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/rank", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val lis = mutableListOf<SManga>()
        document.select("ul > li > div.mh-item > a").forEach {
            lis.add(
                SManga.create().apply {
                    url = it.attr("href")
                    title = it.attr("title")
                    thumbnail_url = "$baseUrl/static/upload$url/cover.jpg"
                },
            )
        }

        return MangasPage(lis, document.selectFirst("#nextPage") != null)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty()) {
            GET("$baseUrl/search?keyword=${query.trim()}", headers)
        } else {
            val url = "$baseUrl/booklist".toHttpUrl().newBuilder().apply {
                if (filters.isEmpty()) {
                    getFilterList()
                } else {
                    filters.filterIsInstance<ParamFilter>().forEach {
                        it.setUrlParam(this)
                    }
                }
                addQueryParameter("page", page.toString())
            }.build()
            GET(url, headers)
        }
    }

    override fun getFilterList(): FilterList = FilterList(
        tagFilter,
        areaFilter,
        endFilter,
    )

    private val tagFilter = ParamFilter(
        "题材",
        "tag",
        listOf(
            "全部" to "全部",
            "青春" to "青春",
            "性感" to "性感",
            "长腿" to "长腿",
            "多人" to "多人",
            "御姐" to "御姐",
            "巨乳" to "巨乳",
            "新婚" to "新婚",
            "媳妇" to "媳妇",
            "暧昧" to "暧昧",
            "清纯" to "清纯",
            "调教" to "调教",
            "少妇" to "少妇",
            "风骚" to "风骚",
            "同居" to "同居",
            "淫乱" to "淫乱",
            "好友" to "好友",
            "女神" to "女神",
            "诱惑" to "诱惑",
            "偷情" to "偷情",
            "出轨" to "出轨",
            "正妹" to "正妹",
            "家教" to "家教",
        ),
    )

    private val areaFilter = ParamFilter(
        "地区",
        "area",
        listOf(
            "全部" to "-1",
            "韩国" to "1",
            "日本" to "2",
            "台湾" to "3",
        ),
    )

    private val endFilter = ParamFilter(
        "进度",
        "end",
        listOf(
            "全部" to "-1",
            "连载" to "0",
            "完结" to "1",
        ),
    )

    private class ParamFilter(
        displayName: String,
        val paramName: String,
        val vals: List<Pair<String, String>>,
        defaultValue: Int = 0,
    ) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
        fun setUrlParam(builder: Builder) {
            builder.addQueryParameter(paramName, vals[state].second)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            baseUrl = runBlocking {
                withContext(Dispatchers.IO) {
                    getRedirectUrl()
                }
            }

            val lis = preferences.getString(APP_MIRROR_URL_LIST, "[]")!!.parseAs<List<String>>()
                .toTypedArray()

            title = "镜像网址"
            summary = "请确认能正常访问$redirectBaseUrl"
            key = APP_MIRROR_URL
            entries = lis
            entryValues = lis

            if (entryValues.isNotEmpty()) {
                setDefaultValue(entryValues[0])
            }

            setOnPreferenceChangeListener { _, click ->
                baseUrl = click as String
                true
            }
        }.let { screen.addPreference(it) }
    }
}

internal const val APP_MIRROR_URL_LIST = "APP_MIRROR_URL_LIST"
internal const val APP_MIRROR_URL = "APP_MIRROR_URL"
