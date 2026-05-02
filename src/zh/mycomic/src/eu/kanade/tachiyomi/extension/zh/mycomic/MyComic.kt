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
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MyComic :
    HttpSource(),
    ConfigurableSource {
    override val baseUrl = "https://mycomic.com"
    override val lang: String = "zh"
    override val name: String = "MyComic"
    override val supportsLatest: Boolean = true

    // Added Accept and Accept-Language to mimic browser and bypass Cloudflare bot detection
    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.9")

    private val preferences by getPreferencesLazy {
        when (getString(PREF_LANGUAGE, "")) {
            "zh-hant" -> edit().putString(PREF_LANGUAGE, "").apply()
            "zh-hans" -> edit().putString(PREF_LANGUAGE, "cn").apply()
        }
    }

    private val requestUrl: String
        get() {
            val lang = preferences.getString(PREF_LANGUAGE, "") ?: ""
            return if (lang.isEmpty()) baseUrl else "$baseUrl/$lang"
        }

    // Popular manga
    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList(popularFilter))

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    // Latest updates
    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList(latestUpdateFilter))

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    // Search manga
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

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.encodedPath.endsWith("/rank")) {
            val document = response.asJsoup()
            return MangasPage(
                document.select("table > tbody > tr > td:nth-child(2) a").map {
                    SManga.create().apply {
                        setUrlWithoutDomain(it.absUrl("href"))
                        title = it.text()
                    }
                },
                false,
            )
        }
        return parseMangaList(response)
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(0),
        RegionFilter(),
        TagFilter(),
        AudienceFilter(),
        YearFilter(),
        StatusFilter(),
    )

    // Manga details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
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
                genre = element.select("> div:nth-child(2) ~ div a").joinToString { it.text() }
            }
            description =
                detailElement.selectFirst("div[data-flux-badge] + div + div div[x-show=show]")
                    ?.text() ?: document.selectFirst("meta[name=description]")?.attr("content")
        }
    }

    // Chapter list
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val data = document.select("div[data-flux-card] + div div[x-data]")
        val notes = data.select("> div:first-child > div:first-child").map(Element::text)
        val dateUpload = DATE_FORMAT.tryParse(document.selectFirst("time[datetime]")?.text())
        return data
            .eachAttr("x-data")
            .map { CHAPTER_REGEX.find(it)!!.value.parseAs<Array<Dto>>() }
            .flatMapIndexed { i, chapters ->
                chapters.map {
                    SChapter.create().apply {
                        name = it.title
                        url = "/chapters/${it.id}"
                        date_upload = dateUpload
                        scanlator = notes[i]
                    }
                }
            }
    }

    // Page list
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img[x-ref]").mapIndexed { index, element ->
            Page(index, imageUrl = element.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Preferences
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(
            ListPreference(screen.context).apply {
                key = PREF_LANGUAGE
                title = "設置首選語言"
                summary = "當前：%s"
                entries = arrayOf("繁體中文", "简体中文")
                entryValues = arrayOf("", "cn")
                setDefaultValue(entryValues[0])
            },
        )
    }

    // Helpers
    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.grid > div.group").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                element.selectFirst("img")!!.let {
                    title = it.attr("alt")
                    thumbnail_url = it.imgAttr()
                }
            }
        }
        val hasNextPage = document.selectFirst("nav[role=navigation] a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
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
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE)
        val CHAPTER_REGEX = Regex("(?<=chapters: )\\[\\{.*?\\}]")
        val popularFilter = SortFilter(2)
        val latestUpdateFilter = SortFilter(1)
        const val PREF_LANGUAGE = "pref_key_lang"
    }
}
