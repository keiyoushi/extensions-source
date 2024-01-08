package eu.kanade.tachiyomi.extension.zh.sixmh

import android.app.Application
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.select.Evaluator
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.random.Random

class SixMH : HttpSource(), ConfigurableSource {
    override val name = "6漫画"
    override val lang = "zh"
    override val supportsLatest = true

    private val isCi = System.getenv("CI") == "true"
    override val baseUrl get() = when {
        isCi -> MIRRORS.zip(MIRROR_NAMES) { domain, name -> "http://www.$domain#$name" }.joinToString()
        else -> _baseUrl
    }

    private val mirrorIndex: Int
    private val pcUrl: String
    private val _baseUrl: String

    init {
        val preferences = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
        val mirrors = MIRRORS
        var index = preferences.getString(MIRROR_PREF, "-1")!!.toInt()
        if (index !in mirrors.indices) {
            index = Random.nextInt(0, mirrors.size)
            preferences.edit().putString(MIRROR_PREF, index.toString()).apply()
        }
        val domain = mirrors[index]

        mirrorIndex = index
        pcUrl = "http://www.$domain"
        _baseUrl = "http://$domain"
    }

    private val json: Json by injectLazy()

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun popularMangaRequest(page: Int) = GET("$pcUrl/rank/1-$page.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val imgSelector = Evaluator.Tag("img")
        val items = document.selectFirst(Evaluator.Class("cy_list_mh"))!!.children().map {
            SManga.create().apply {
                val link = it.child(1).child(0)
                url = link.attr("href")
                title = link.ownText()
                thumbnail_url = it.selectFirst(imgSelector)!!.attr("src")
            }
        }
        val hasNextPage = document.selectFirst(Evaluator.Class("thisclass"))?.nextElementSibling() != null
        return MangasPage(items, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$pcUrl/rank/5-$page.html", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = pcUrl.toHttpUrl().newBuilder()
                .addEncodedPathSegment("search.php")
                .addQueryParameter("keyword", query)
                .toString()
            return GET(url, headers)
        } else {
            filters.filterIsInstance<PageFilter>().firstOrNull()?.run {
                return GET("$pcUrl$path$page.html", headers)
            }
            return popularMangaRequest(page)
        }
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(GET(pcUrl + manga.url, headers))
            .asObservableSuccess().map(::mangaDetailsParse)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val result = SManga.create().apply {
            val box = document.selectFirst(Evaluator.Class("cy_info"))!!
            val details = box.getElementsByTag("span")
            author = details[0].text().removePrefix("作者：")
            status = when (details[1].text().removePrefix("状态：").trimStart()) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = buildList {
                add(details[2].ownText().removePrefix("类别："))
                details[3].ownText().removePrefix("标签：").split(Regex("[ -~]+"))
                    .filterTo(this) { it.isNotEmpty() }
            }.joinToString()
            description = box.selectFirst(Evaluator.Tag("p"))!!.ownText()
            thumbnail_url = box.selectFirst(Evaluator.Tag("img"))!!.run {
                attr("data-src").ifEmpty { attr("src") }
            }
        }
        return result
    }

    override fun chapterListRequest(manga: SManga) = GET(pcUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val list = document.selectFirst(Evaluator.Class("cy_plist"))!!
            .child(0).children().map {
                val element = it.child(0)
                SChapter.create().apply {
                    url = element.attr("href")
                    name = element.text()
                }
            }
            as ArrayList

        if (mirrorIndex == 0) { // 6Manhua
            document.selectFirst(Evaluator.Id("zhankai"))?.let { element ->
                val path = '/' + response.request.url.pathSegments[0] + '/'
                val body = FormBody.Builder().apply {
                    addEncoded("id", element.attr("data-id"))
                    addEncoded("id2", element.attr("data-vid"))
                }.build()
                client.newCall(POST("$pcUrl/bookchapter/", headers, body)).execute()
                    .parseAs<List<ChapterDto>>().mapTo(list) { it.toSChapter(path) }
            }
        } else { // Qixi Manhua
            if (document.selectFirst(Evaluator.Class("morechp")) != null) {
                val id = response.request.url.pathSegments[0]
                val path = "/$id/"
                val body = FormBody.Builder().addEncoded("id", id).build()
                client.newCall(POST("$pcUrl/chapterlist/", headers, body)).execute()
                    .parseAs<QixiResponseDto>().data.list.mapTo(list) { it.toSChapter(path) }
            }
        }

        if (list.isNotEmpty()) {
            document.selectFirst(".cy_zhangjie_top font")?.run {
                list[0].date_upload = dateFormat.parse(ownText())?.time ?: 0
            }
        }
        return list
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = Unpacker.unpack(response.body.string(), "[", "]")
            .ifEmpty { return emptyList() }
            .replace("\\u0026", "&")
            .replace("\\", "")
            .removeSurrounding("\"").split("\",\"")
        return result.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used.")

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(body.byteStream())
    }

    override fun getFilterList() = FilterList(listOf(PageFilter()))

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            val names = MIRROR_NAMES

            key = MIRROR_PREF
            title = "镜像站点（重启生效）"
            summary = "%s"
            entries = names
            entryValues = Array(names.size, Int::toString)
            setDefaultValue("0")
        }.let(screen::addPreference)
    }

    companion object {

        const val MIRROR_PREF = "MIRROR"

        /** Note: mirror index affects [chapterListParse] */
        val MIRRORS get() = arrayOf("sixmanhua.com", "qiximh3.com")
        val MIRROR_NAMES get() = arrayOf("6漫画", "七夕漫画")

        private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    }
}
