package eu.kanade.tachiyomi.extension.zh.roumanwu

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.max

class Roumanwu : ParsedHttpSource(), ConfigurableSource {
    override val name = "肉漫屋"
    override val lang = "zh"
    override val supportsLatest = true

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val baseUrl = MIRRORS[
        max(MIRRORS.size - 1, preferences.getString(MIRROR_PREF, MIRROR_DEFAULT)!!.toInt()),
    ]

    override val client = network.cloudflareClient.newBuilder().addInterceptor(ScrambledImageInterceptor).build()

    private val imageUrlRegex = """\\"imageUrl\\":\\"(?<imageUrl>[^\\]+)""".toRegex()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/home", headers)
    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaSelector(): String = "div.px-1 > div:matches(正熱門|今日最佳|本週熱門) .grid a[href*=/books/]"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.truncate").text()
        url = element.attr("href")
        thumbnail_url = element.select("div.bg-cover").attr("style").substringAfter("background-image:url(\"").substringBefore("\")")
    }
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        val uniqueMangas = mangas.distinctBy { it.url }

        val hasNextPage = popularMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(uniqueMangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun latestUpdatesSelector(): String = "div.px-1 > div:contains(最近更新) .grid a[href*=/books/]"
    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.truncate").text()
        url = element.attr("href")
        thumbnail_url = element.select("div.bg-cover").attr("style").substringAfter("background-image:url(\"").substringBefore("\")")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        if (query.isNotBlank()) {
            GET("$baseUrl/search?term=$query&page=${page - 1}", headers)
        } else {
            val parts = filters.filterIsInstance<UriPartFilter>().joinToString("") { it.toUriPart() }
            GET("$baseUrl/books?page=${page - 1}$parts", headers)
        }
    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaSelector(): String = "a[href*=/books/]"
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.truncate").text()
        url = element.attr("href")
        thumbnail_url = element.select("div.bg-cover").attr("style").substringAfter("background-image:url(\"").substringBefore("\")")
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("div.basis-3\\/5 > div.text-xl").text()
        thumbnail_url = baseUrl + document.select("main > div:first-child img").attr("src")
        author = document.select("div.basis-3\\/5 > div:nth-child(3) span").text()
        artist = author
        status = when (document.select("div.basis-3\\/5 > div:nth-child(4) span").text()) {
            "連載中" -> SManga.ONGOING
            "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = document.select("div.basis-3\\/5 > div:nth-child(6) span").text().replace(",", ", ")
        description = document.select("p:contains(簡介:)").text().substring(3)
    }

    override fun chapterListSelector(): String = "a[href~=/books/.*/\\d+]"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.attr("href")
        name = element.text()
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun pageListParse(document: Document): List<Page> {
        val images = document.selectFirst("script:containsData(imageUrl)")?.data()
            ?.let { content ->
                imageUrlRegex
                    .findAll(content).map { it.groups["imageUrl"]?.value }
                    .toList()
            } ?: return emptyList()

        return images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("提示：搜尋時篩選無效"),
        TagFilter(),
        StatusFilter(),
        SortFilter(),
    )

    private abstract class UriPartFilter(name: String, values: Array<String>) : Filter.Select<String>(name, values) {
        abstract fun toUriPart(): String
    }

    private class TagFilter : UriPartFilter("標籤", TAGS) {
        override fun toUriPart() = if (state == 0) "" else "&tag=${values[state]}"
    }

    private class StatusFilter : UriPartFilter("狀態", arrayOf("全部", "連載中", "已完結")) {
        override fun toUriPart() =
            when (state) {
                1 -> "&continued=true"
                2 -> "&continued=false"
                else -> ""
            }
    }

    private class SortFilter : UriPartFilter("排序", arrayOf("更新日期", "評分")) {
        override fun toUriPart() = if (state == 0) "" else "&sort=rating"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPref = androidx.preference.ListPreference(screen.context).apply {
            key = MIRROR_PREF
            title = MIRROR_PREF_TITLE
            entries = MIRRORS_DESC
            entryValues = MIRRORS.indices.map(Int::toString).toTypedArray()
            summary = MIRROR_PREF_SUMMARY

            setDefaultValue(MIRROR_DEFAULT)
        }
        screen.addPreference(mirrorPref)
    }

    companion object {
        private const val MIRROR_PREF = "MIRROR"
        private const val MIRROR_PREF_TITLE = "使用鏡像網址"
        private const val MIRROR_PREF_SUMMARY = "使用鏡像網址。重啟軟體生效。"

        // 地址: https://rou.pub/dizhi
        private val MIRRORS get() = arrayOf("https://rouman5.com", "https://roum18.xyz")
        private val MIRRORS_DESC get() = arrayOf("主站", "鏡像")
        private const val MIRROR_DEFAULT = 1.toString() // use mirror

        private val TAGS get() = arrayOf("全部", "\u6B63\u59B9", "\u604B\u7231", "\u51FA\u7248\u6F2B\u753B", "\u8089\u617E", "\u6D6A\u6F2B", "\u5927\u5C3A\u5EA6", "\u5DE8\u4E73", "\u6709\u592B\u4E4B\u5A66", "\u5973\u5927\u751F", "\u72D7\u8840\u5287", "\u540C\u5C45", "\u597D\u53CB", "\u8ABF\u6559", "\u52A8\u4F5C", "\u5F8C\u5BAE", "\u4E0D\u502B")
    }
}
