package eu.kanade.tachiyomi.extension.zh.baimangu

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class Baimangu : ConfigurableSource, ParsedHttpSource() {
    override val lang = "zh"
    override val supportsLatest = true
    override val name = "百漫谷"

    // Preference setting
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val baseUrl = preferences.getString(MAINSITE_URL_PREF, MAINSITE_URL_PREF_DEFAULT)!!

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(
            baseUrl.toHttpUrlOrNull()!!,
            preferences.getString(MAINSITE_RATEPERMITS_PREF, MAINSITE_RATEPERMITS_PREF_DEFAULT)!!.toInt(),
            preferences.getString(MAINSITE_RATEPERIOD_PREF, MAINSITE_RATEPERIOD_PREF_DEFAULT)!!.toLong(),
            TimeUnit.MILLISECONDS,
        )
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    // The following images don't seem to work, hence we do not bother to retrieve them
    private val invalidCoverImageDomains = arrayOf("serial-online", "res.cocomanga.com")

    // Common
    private var commonSelector = "li.fed-list-item"
    private var commonNextPageSelector = "a.fed-btns-info.fed-rims-info:nth-last-child(4)"
    private fun commonMangaFromElement(element: Element): SManga {
        val picElement = element.select("a.fed-list-pics").first()!!
        val picUrl = picElement.attr("data-original")
        val manga = SManga.create().apply {
            title = element.select("a.fed-list-title").first()!!.text()

            if (!invalidCoverImageDomains.any { picUrl.contains(it) }) {
                thumbnail_url = picUrl
            }
        }

        manga.setUrlWithoutDomain(picElement.attr("href"))

        return manga
    }

    // Popular Manga
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/vodshow/4--hits------$page---.html", headers)
    override fun popularMangaNextPageSelector() = commonNextPageSelector
    override fun popularMangaSelector() = commonSelector
    override fun popularMangaFromElement(element: Element) = commonMangaFromElement(element)

    // Latest Updates
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/fenlei/2-$page.html", headers)
    override fun latestUpdatesNextPageSelector() = commonNextPageSelector
    override fun latestUpdatesSelector() = commonSelector
    override fun latestUpdatesFromElement(element: Element) = commonMangaFromElement(element)

    // Filter
    // 最新漫画 - 1
    // 漫画更新 - 2
    // 更多漫画 - 3
    // 漫画大全 - 4
    private class ChannelFilter : Filter.Select<String>(
        "Channel",
        arrayOf(
            "最新漫画",
            "漫画更新",
            "更多漫画",
            "漫画大全",
        ),
        3, // means 漫画大全 (4)
    )

    private class SortFilter : Filter.Select<String>("排序", arrayOf("按时间", "按人气", "按评分"), 0)

    override fun getFilterList() = FilterList(
        ChannelFilter(),
        SortFilter(),
    )

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/vodsearch/$query----------$page---", headers)
        } else {
            var channelValue = "4" // 漫画大全
            var sortValue = "time" // 按时间

            filters.forEach { filter ->
                when (filter) {
                    is ChannelFilter -> {
                        channelValue = arrayOf("1", "2", "3", "4")[filter.state]
                    }
                    is SortFilter -> {
                        sortValue = arrayOf("time", "hits", "score")[filter.state]
                    }
                    else -> {}
                }
            }

            // https://www.darpou.com/vodshow/2-----------.html
            // https://www.darpou.com/vodshow/2--hits------3---.html

            val url = "$baseUrl/vodshow/$channelValue--$sortValue------$page---"

            GET(url, headers)
        }
    }

    override fun searchMangaNextPageSelector() = commonNextPageSelector
    override fun searchMangaSelector() = "dl.fed-deta-info, $commonSelector"
    override fun searchMangaFromElement(element: Element): SManga {
        if (element.tagName() == "li") {
            return commonMangaFromElement(element)
        }

        val picElement = element.select("a.fed-list-pics").first()!!
        return SManga.create().apply {
            title = element.select("dd.fed-deta-content a:first-child").first()!!.text()
            thumbnail_url = picElement.attr("data-original")
            setUrlWithoutDomain(picElement.attr("href"))
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val picElement = document.select("a.fed-list-pics").first()!!
        val picUrl = picElement.attr("data-original")
        val detailElements = document.select("dd.fed-deta-content ul.fed-part-rows")
        return SManga.create().apply {
            title = document.select("h1.fed-part-eone").first()!!.text().trim()

            if (!invalidCoverImageDomains.any { picUrl.contains(it) }) {
                thumbnail_url = picUrl
            }

            // They don't seem to show the status, unless we query from the category, but that may be inaccurate

            author = detailElements.select("li:nth-child(1) a").firstOrNull()?.text()?.trim()

            genre = detailElements.select("li.fed-show-md-block:nth-last-child(2) a:not(:empty)").joinToString { it.text().trim() }

            // It has both "简介：" and "简介" in the description
            description = detailElements.select("li.fed-show-md-block:nth-last-child(1)")
                .firstOrNull()?.text()?.replace("简介：", "", ignoreCase = true)
                ?.replace("简介", "", ignoreCase = true)?.trim()
        }
    }

    override fun chapterListSelector(): String = "div.fed-play-item ul.fed-part-rows:last-child a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.text().trim()
            setUrlWithoutDomain(element.attr("href"))
        }
    }

    // Reverse the order of the chapter list
    override fun chapterListParse(response: Response): List<SChapter> =
        super.chapterListParse(response).reversed()

    override fun imageUrlParse(document: Document) = ""

    override fun pageListParse(document: Document): List<Page> {
        val oScriptUrl = extractOScriptUrl(document)

        val oScriptResp = client.newCall(GET(oScriptUrl)).execute()

        if (!oScriptResp.isSuccessful) {
            throw Error("Failed to request OScript URL")
        }

        val content = oScriptResp.body.string()
        return extractPagesFromOScript(content)
    }

    private fun extractOScriptUrl(document: Document): String {
        val theScriptData = document.selectFirst("script:containsData(oScript.src)")?.data()
            ?: throw Exception("Unable to find OScript")

        val pattern = Pattern.compile("src(\\s*)=(\\s*)\"(.+)\";")
        val matcher = pattern.matcher(theScriptData)

        if (matcher.find()) {
            return matcher.group(3) ?: throw Exception("Unable to extract OScript")
        }

        throw Error("Unable to match for OScript")
    }

    private fun extractPagesFromOScript(content: String): List<Page> {
        if (content.isEmpty()) {
            throw Error("Empty OScript")
        }

        if (!content.contains("show(") || !content.contains("<img")) {
            throw Error("Unexpected OScript Content")
        }

        val regex = Regex("src=\"([^>]+)\"")
        val matches = regex.findAll(content, 0)
        return matches.mapIndexed { i, match ->
            Page(i, "", match.groupValues[1])
        }.toList()
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val mainSiteUrlPreference = androidx.preference.EditTextPreference(screen.context).apply {
            key = MAINSITE_URL_PREF
            title = MAINSITE_URL_PREF_TITLE
            summary = MAINSITE_URL_PREF_SUMMARY

            setDefaultValue(MAINSITE_URL_PREF_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(MAINSITE_URL_PREF, newValue as String).commit()
                    Toast.makeText(screen.context, TOAST_RESTART, Toast.LENGTH_LONG).show()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        val mainSiteRatePermitsPreference = androidx.preference.ListPreference(screen.context).apply {
            key = MAINSITE_RATEPERMITS_PREF
            title = MAINSITE_RATEPERMITS_PREF_TITLE
            entries = MAINSITE_RATEPERMITS_PREF_ENTRIES_ARRAY
            entryValues = MAINSITE_RATEPERMITS_PREF_ENTRIES_ARRAY
            summary = MAINSITE_RATEPERMITS_PREF_SUMMARY

            setDefaultValue(MAINSITE_RATEPERMITS_PREF_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(MAINSITE_RATEPERMITS_PREF, newValue as String).commit()
                    Toast.makeText(screen.context, TOAST_RESTART, Toast.LENGTH_LONG).show()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        val mainSiteRatePeriodPreference = androidx.preference.ListPreference(screen.context).apply {
            key = MAINSITE_RATEPERIOD_PREF
            title = MAINSITE_RATEPERIOD_PREF_TITLE
            entries = MAINSITE_RATEPERIOD_PREF_ENTRIES_ARRAY
            entryValues = MAINSITE_RATEPERIOD_PREF_ENTRIES_ARRAY
            summary = MAINSITE_RATEPERIOD_PREF_SUMMARY

            setDefaultValue(MAINSITE_RATEPERIOD_PREF_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(MAINSITE_RATEPERIOD_PREF, newValue as String).commit()
                    Toast.makeText(screen.context, TOAST_RESTART, Toast.LENGTH_LONG).show()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(mainSiteUrlPreference)
        screen.addPreference(mainSiteRatePermitsPreference)
        screen.addPreference(mainSiteRatePeriodPreference)
    }

    companion object {
        private const val TOAST_RESTART = "請重新启动tachiyomi"

        // ---------------------------------------------------------------------------------------

        private const val MAINSITE_URL_PREF = "mainSiteUrlPreference"
        private const val MAINSITE_URL_PREF_DEFAULT = "https://www.darpou.com/"

        private const val MAINSITE_URL_PREF_TITLE = "主站URL"
        private const val MAINSITE_URL_PREF_SUMMARY = "需要重启软件以生效。\n默认值：$MAINSITE_URL_PREF_DEFAULT"

        // ---------------------------------------------------------------------------------------

        private const val MAINSITE_RATEPERMITS_PREF = "mainSiteRatePermitsPreference"
        private const val MAINSITE_RATEPERMITS_PREF_DEFAULT = "6"

        /** main site's connection limit */
        private const val MAINSITE_RATEPERMITS_PREF_TITLE = "主站连接限制"

        /** This value affects connection request amount to main site. Lowering this value may reduce the chance to get HTTP 403 error, but loading speed will be slower too. Tachiyomi restart required. Current value: %s" */
        private const val MAINSITE_RATEPERMITS_PREF_SUMMARY = "此值影响主站的连接请求量。需要重启软件以生效。\n默认值：$MAINSITE_RATEPERMITS_PREF_DEFAULT \n当前值：%s"
        private val MAINSITE_RATEPERMITS_PREF_ENTRIES_ARRAY = (1..10).map { i -> i.toString() }.toTypedArray()

        // ---------------------------------------------------------------------------------------

        private const val MAINSITE_RATEPERIOD_PREF = "mainSiteRatePeriodMillisPreference"
        private const val MAINSITE_RATEPERIOD_PREF_DEFAULT = "1000"

        /** main site's connection limit period */
        private const val MAINSITE_RATEPERIOD_PREF_TITLE = "主站连接限制期"

        /** This value affects the delay when hitting the connection limit to main site. Increasing this value may reduce the chance to get HTTP 403 error, but loading speed will be slower too. Tachiyomi restart required. Current value: %s" */
        private const val MAINSITE_RATEPERIOD_PREF_SUMMARY = "此值影响主站点连接限制时的延迟（毫秒）。需要重启软件以生效。\n默认值：$MAINSITE_RATEPERIOD_PREF_DEFAULT\n当前值：%s"
        private val MAINSITE_RATEPERIOD_PREF_ENTRIES_ARRAY = (500..6000 step 500).map { i -> i.toString() }.toTypedArray()
    }
}
