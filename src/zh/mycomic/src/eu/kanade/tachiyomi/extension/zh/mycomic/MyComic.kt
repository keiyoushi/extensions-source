package eu.kanade.tachiyomi.extension.zh.mycomic

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MyComic :
    KeiSource(),
    ConfigurableSource {
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

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter.POPULAR))

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter.LATEST))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sortSelected = filters.firstInstanceOrNull<SortFilter>()?.selected
        val isRankFilter = sortSelected?.startsWith(SortFilter.RANK_PREFIX) == true

        val url = if (isRankFilter) {
            "$requestUrl/rank"
        } else {
            "$requestUrl/comics"
        }.toHttpUrl().newBuilder()
        if (!isRankFilter && query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        sortSelected
            ?.takeUnless { it == SortFilter.RANK_PREFIX } // skip implicit default rank (日排行)
            ?.removePrefix(SortFilter.RANK_PREFIX)
            ?.let { url.addQueryParameter("sort", it) }

        filters.list.filterIsInstance<UriPartFilter>().forEach { filter ->
            if (filter is SortFilter) return@forEach
            filter.selected?.let { url.addQueryParameter(filter.key, it) }
        }

        if (!isRankFilter && page > 1) {
            url.addQueryParameter("page", page.toString())
        }

        val document = client.get(url.build()).asJsoup()
        if (isRankFilter) {
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
        return parseMangaList(document)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val hasCnPrefix = url.pathSegments.firstOrNull() == "cn"
        val segments = url.pathSegments.drop(if (hasCnPrefix) 1 else 0)
        when (segments.getOrNull(0)) {
            "chapters" -> {
                if (segments.getOrNull(1).isNullOrEmpty()) return null
                val href = client.get(url).asJsoup()
                    .selectFirst("a[data-flux-button]")
                    ?.absUrl("href") ?: return null
                return getMangaByUrl(href.toHttpUrl())
            }
            "comics" -> {
                if (segments.getOrNull(1).isNullOrEmpty()) return null
                val mangaUrl = baseUrl.toHttpUrl().newBuilder().apply {
                    if (hasCnPrefix) addPathSegment("cn")
                    addPathSegment("comics")
                    addPathSegment(segments[1])
                }.build()
                return mangaDetailsParse(client.get(mangaUrl).asJsoup()).apply {
                    setUrlWithoutDomain(mangaUrl.toString())
                    initialized = true
                }
            }
            else -> return null
        }
    }

    private fun parseMangaList(document: Document): MangasPage {
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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document): SManga {
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

    private fun chapterListParse(document: Document): List<SChapter> {
        val data = document.select("div[data-flux-card] + div div[x-data]")
        val notes = data.select("> div:first-child > div:first-child").map(Element::text)
        val dateUpload = DATE_FORMAT.tryParseDate(document.selectFirst("time[datetime]")?.text())
        return data
            .eachAttr("x-data")
            .map { CHAPTER_REGEX.find(it)!!.value.parseAs<Array<Dto>>() }
            .flatMapIndexed { i, chapters ->
                chapters.map { it.toSChapter(dateUpload, notes[i]) }
            }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("img[x-ref]").mapIndexed { index, element ->
            Page(index, imageUrl = element.imgAttr())
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(0),
        RegionFilter(),
        TagFilter(),
        AudienceFilter(),
        YearFilter(),
        StatusFilter(),
    )

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

    private fun Element.imgAttr() = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    companion object {
        val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
        val CHAPTER_REGEX = Regex("(?<=chapters: )\\[\\{.*?\\}]")
        const val PREF_LANGUAGE = "pref_key_lang"
    }
}
