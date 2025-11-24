package eu.kanade.tachiyomi.extension.zh.roumanwu

import android.content.SharedPreferences
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

class Roumanwu : HttpSource(), ConfigurableSource {
    override val name = "肉漫屋"
    override val lang = "zh"
    override val supportsLatest = true

    private val preferences: SharedPreferences = getPreferences()

    override val baseUrl = MIRRORS[
        max(MIRRORS.size - 1, preferences.getString(MIRROR_PREF, MIRROR_DEFAULT)!!.toInt()),
    ]

    override val client = network.cloudflareClient.newBuilder().addInterceptor(ScrambledImageInterceptor()).build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/home", headers)

    private fun parseEntries(container: Element): List<SManga> {
        return container.select("a[href*=/books/]").map {
            SManga.create().apply {
                title = it.selectFirst("div.truncate")!!.text()
                url = it.attr("href")
                thumbnail_url = it.selectFirst("div.bg-cover")!!.attr("style").substringAfter("background-image:url(\"").substringBefore("\")")
            }
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return parseHomePage(document, Regex("正熱門|今日最佳|本週熱門"))
    }

    private fun parseHomePage(document: Document, sections: Regex): MangasPage {
        val entries = document.selectFirst("div.px-1")!!.children().flatMap { section ->
            if (section.child(0).text().contains(sections)) {
                parseEntries(section)
            } else {
                emptyList()
            }
        }.distinctBy { it.url }

        return MangasPage(entries, false)
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return parseHomePage(document, Regex("最近更新"))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        if (query.isNotBlank()) {
            GET("$baseUrl/search?term=$query&page=${page - 1}", headers)
        } else {
            val parts = filters.filterIsInstance<UriPartFilter>().joinToString("") { it.toUriPart() }
            GET("$baseUrl/books?page=${page - 1}$parts", headers)
        }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = parseEntries(document)
        val thisPage = response.request.url.queryParameter("page")!!
        val nextPage = document.selectFirst("div.justify-end > a:contains(下一頁)")!!
            .absUrl("href").toHttpUrl().queryParameter("page")!!
        return MangasPage(entries, thisPage != nextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        val infobox = parseInfobox(document).iterator()

        title = infobox.next()
        thumbnail_url = document.selectFirst("div.basis-2\\/5 img")!!.absUrl("src")
            .run { toHttpUrl().queryParameter("url") ?: this }
        description = document.selectFirst("p:contains(簡介:)")!!.text().substring(3)

        val genres = ArrayList<String>()
        for (text in infobox) {
            val value = text.drop(3).trimStart()
            if (value.isEmpty()) continue
            when (text.take(3)) {
                "別名:" -> if (value != title) description = "$text\n\n$description"
                "作者:" -> author = value
                "狀態:" -> status = when (value) {
                    "連載中" -> SManga.ONGOING
                    "已完結" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }

                "地區:" -> genres.add(value)
                "標籤:" -> genres.addAll(value.split(","))
            }
        }
        genre = genres.joinToString()
    }

    private fun parseInfobox(document: Document): List<String> {
        val infobox = document.selectFirst("div.basis-3\\/5")!!.children()
        check(infobox.size >= 6 && infobox[0].hasClass("text-xl"))
        return infobox.map { it.text() }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select("a[href~=/books/.*/\\d+]").map {
            SChapter.create().apply {
                url = it.attr("href")
                name = it.text()
            }
        }.asReversed()
        if (chapters.isNotEmpty()) {
            val dateFormat = SimpleDateFormat("M/d/yyyy", Locale.US)
            for (text in parseInfobox(document).asReversed()) {
                val date = dateFormat.parse(text, ParsePosition(0)) ?: continue
                chapters[0].date_upload = date.time
                break
            }
        }
        return chapters
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // Rendered HTML might have links sitting on the boundary of two scripts
        return super.pageListRequest(chapter).newBuilder().addHeader("rsc", "1").build()
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val regex = Regex(""""imageUrl":"([^"]+)""")
        return regex.findAll(html).mapIndexedTo(ArrayList()) { index, match ->
            Page(index, imageUrl = match.groupValues[1])
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("提示：搜尋時篩選無效"),
        StatusFilter(),
    )

    private abstract class UriPartFilter(name: String, values: Array<String>) : Filter.Select<String>(name, values) {
        abstract fun toUriPart(): String
    }

    private class StatusFilter : UriPartFilter("狀態", arrayOf("全部", "連載中", "已完結")) {
        override fun toUriPart() =
            when (state) {
                1 -> "&continued=true"
                2 -> "&continued=false"
                else -> ""
            }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPref = ListPreference(screen.context).apply {
            key = MIRROR_PREF
            title = "使用鏡像網址"
            entries = MIRRORS_DESC
            entryValues = Array(MIRRORS.size, Int::toString)
            summary = "使用鏡像網址。重啟軟體生效。"

            setDefaultValue(MIRROR_DEFAULT)
        }
        screen.addPreference(mirrorPref)
    }

    companion object {
        private const val MIRROR_PREF = "MIRROR"

        // 地址: https://rou.pub/dizhi or https://rdz1.xyz/dizhi
        private val MIRRORS get() = arrayOf("https://rouman5.com", "https://roum22.xyz")
        private val MIRRORS_DESC get() = arrayOf("主站", "鏡像")
        private const val MIRROR_DEFAULT = 1.toString() // use mirror
    }
}
