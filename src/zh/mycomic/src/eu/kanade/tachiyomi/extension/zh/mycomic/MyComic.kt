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
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MyComic :
    ParsedHttpSource(),
    ConfigurableSource {
    override val baseUrl = "https://mycomic.com"
    override val lang: String = "zh"
    override val name: String = "MyComic"
    override val supportsLatest: Boolean = true

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")
    private val preferences by getPreferencesLazy()
    private val requestUrl: String
        get() = if (preferences.getString(PREF_KEY_LANG, "") == "zh-hans") {
            "$baseUrl/cn"
        } else {
            baseUrl
        }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div[data-flux-card] + div div[x-data]")
            .eachAttr("x-data")
            .map {
                it.substringAfter("chapters:").substringBefore("\n").trim().removeSuffix(",")
            }
            .map {
                it.parseAs<List<Chapter>>()
            }
            .flatten()
            .map {
                SChapter.create().apply {
                    name = it.title
                    // Since the images included in the chapter do not distinguish between Traditional and Simplified Chinese, the default URL will be used uniformly here.
                    // Additionally, using different URLs would create more issues, so it's best to keep the URL consistent.
                    url = "/chapters/${it.id}"
                }
            }
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList(latestUpdateFilter))

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val detailElement = document.selectFirst("div[data-flux-card]")!!
        return SManga.create().apply {
            title = detailElement.selectFirst("div[data-flux-heading]")!!.text()
            thumbnail_url = detailElement.selectFirst("img.object-cover")?.imgAttr()
            status = detailElement.selectFirst("div[data-flux-badge]")?.text().let {
                when (it) {
                    "连载中", "連載中" -> SManga.ONGOING
                    "已完结", "已完結" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
            detailElement.selectFirst("div[data-flux-badge] + div")?.let { element ->
                author = element.selectFirst(":first-child a")?.text()
                genre = element.select(":nth-child(3) a").joinToString { it.text() }
            }
            description =
                detailElement.selectFirst("div[data-flux-badge] + div + div div[x-show=show]")
                    ?.text() ?: document.selectFirst("meta[name=description]")?.attr("content")
        }
    }

    override fun pageListParse(document: Document): List<Page> = document.select("img[x-ref]").mapIndexed { index, element ->
        Page(index, imageUrl = element.imgAttr())
    }

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList(popularFilter))

    override fun popularMangaSelector() = searchMangaSelector()

    override fun searchMangaSelector() = "div.grid > div.group"

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.encodedPath == "/rank") {
            val doc = response.asJsoup()
            return MangasPage(
                doc.select("table > tbody > tr > td:nth-child(2) a").map {
                    SManga.create().apply {
                        setUrlWithoutDomain(it.absUrl("href"))
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

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        element.selectFirst("img")!!.let {
            title = it.attr("alt")
            thumbnail_url = it.imgAttr()
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

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(0),
        RegionFilter(),
        TagFilter(),
        AudienceFilter(),
        YearFilter(),
        StatusFilter(),
    )

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

    private fun Element.imgAttr() = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    companion object {
        val popularFilter = SortFilter(2)
        val latestUpdateFilter = SortFilter(1)

        const val PREF_KEY_LANG = "pref_key_lang"
    }
}
