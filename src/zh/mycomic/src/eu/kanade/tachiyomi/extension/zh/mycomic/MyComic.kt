package eu.kanade.tachiyomi.extension.zh.mycomic

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class MyComic : ParsedHttpSource(), ConfigurableSource {
    override val baseUrl = "https://mycomic.com"
    override val lang: String
        get() = "zh"
    override val name: String
        get() = "MyComic"
    override val supportsLatest: Boolean
        get() = true

    override fun headersBuilder() = super.headersBuilder().add("referer", baseUrl)
    private val json by injectLazy<Json>()
    private val preferences by getPreferencesLazy()
    private val requestUrl: String
        get() = if (preferences.getString(PREF_KEY_LANG, "") == "zh-hans") {
            "$baseUrl/cn"
        } else {
            baseUrl
        }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val data = doc.select("div[data-flux-card] + div div[x-data]").attr("x-data")
        val chapters =
            data.substringAfter("chapters:").substringBefore("\n").trim().removeSuffix(",")
        return json.decodeFromString<JsonArray>(chapters).map {
            SChapter.create().apply {
                name = it.jsonObject["title"]!!.jsonPrimitive.content
                // Since the images included in the chapter do not distinguish between Traditional and Simplified Chinese, the default URL will be used uniformly here.
                // Additionally, using different URLs would create more issues, so it's best to keep the URL consistent.
                url = "/chapters/${it.jsonObject["id"]!!.jsonPrimitive.content}"
            }
        }
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) =
        searchMangaRequest(page, "", FilterList(latestUpdateFilter))

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val detailElement = document.select("div[data-flux-card]")
        return SManga.create().apply {
            title = detailElement.select("div[data-flux-heading]").text().ifEmpty { title }
            thumbnail_url = detailElement.select("img.object-cover").attr("src")
            status = detailElement.select("div[data-flux-badge]").text().let {
                when (it) {
                    "连载中", "連載中" -> SManga.ONGOING
                    "已完结", "已完結" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
            detailElement.select("div[data-flux-badge] + div").let {
                author = it.select(":first-child a").text()
                genre = it.select(":nth-child(3) a").joinToString { e -> e.text() }
            }
            description =
                detailElement.select("div[data-flux-badge] + div + div div[x-show=show]").text()
                    .ifEmpty {
                        document.select("meta[name=description]").attr("content")
                    }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img[x-ref]").mapIndexed { index, element ->
            val url = if (element.hasAttr("data-src")) {
                element.attr("data-src")
            } else {
                element.attr("src")
            }
            Page(index, imageUrl = url)
        }
    }

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaRequest(page: Int) =
        searchMangaRequest(page, "", FilterList(popularFilter))

    override fun popularMangaSelector() = searchMangaSelector()

    override fun searchMangaSelector() = "div.grid > div.group"

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.encodedPath == "/rank") {
            val doc = response.asJsoup()
            return MangasPage(
                doc.select("table > tbody > tr > td:nth-child(2) a").map {
                    SManga.create().apply {
                        setUrlWithoutDomain(it.attr("href"))
                        title = it.text()
                        // ranking page not support thumbnail
                    }
                },
                false,
            )
        } else {
            return super.searchMangaParse(response)
        }
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            element.selectFirst("img")!!.let {
                title = it.attr("alt")
                thumbnail_url = if (it.hasAttr("data-src")) {
                    it.attr("data-src")
                } else {
                    it.attr("src")
                }
            }
        }
    }

    override fun searchMangaNextPageSelector() = "nav[role=navigation] a[rel=next]"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortFilter = filters.firstInstance<SortFilter>()
        val isRankFilter = sortFilter.selected.startsWith(SortFilter.RANK_PREFIX)
        val url = if (isRankFilter) {
            "$requestUrl/rank"
        } else {
            "$requestUrl/comics"
        }.toHttpUrl().newBuilder()
        if (!isRankFilter) {
            url.addQueryParameterIfNotEmpty("q", query)
        }
        url.addQueryParameterIfNotEmpty(
            sortFilter.key,
            sortFilter.selected.removePrefix(SortFilter.RANK_PREFIX),
        )
        filters.list.filterIsInstance<UriPartFilter>().forEach {
            if (it is SortFilter) {
                return@forEach
            }
            url.addQueryParameterIfNotEmpty(it.key, it.selected)
        }
        if (!isRankFilter && page > 1) {
            url.addQueryParameter("page", page.toString())
        }
        return GET(url.build(), headers = headers)
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            SortFilter(0),
            RegionFilter(),
            TagFilter(),
            AudienceFilter(),
            YearFilter(),
            StatusFilter(),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(
            ListPreference(screen.context).apply {
                key = PREF_KEY_LANG
                title = "設置首選語言"
                summary = "當前：%s"
                entries = arrayOf("繁體中文", "简体中文")
                entryValues = arrayOf("zh-hant", "zh-hans")
                setDefaultValue(entryValues[0])
            },
        )
    }

    private fun HttpUrl.Builder.addQueryParameterIfNotEmpty(name: String, value: String) {
        if (value.isNotEmpty()) {
            addQueryParameter(name, value)
        }
    }

    companion object {
        val popularFilter = SortFilter(2)
        val latestUpdateFilter = SortFilter(1)

        const val PREF_KEY_LANG = "pref_key_lang"
    }
}
