package eu.kanade.tachiyomi.extension.en.ebookrenta

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class EbookRenta :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addCookie(listOf("r18" to "1", "rbc" to "1002"))
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 3 }))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter()))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/renta/sc/frm/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("word", query)
            addQueryParameter("sort", filters.firstInstance<SortFilter>().value)
            addFilter("rsi", filters.firstInstanceOrNull<FormatFilter>())
            addFilter("genm", filters.firstInstanceOrNull<GenreFilter>())
            addFilter("gend", filters.firstInstanceOrNull<CategoryFilter>())
            addFilter("keyword", filters.firstInstanceOrNull<KeywordFilter>())
            addFilter("deals", filters.firstInstanceOrNull<DealsFilter>())
            addFilter("keyword_sales", filters.firstInstanceOrNull<OnSaleFilter>())
            addQueryParameter("type", "desc")
            addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url).asJsoup()
        val mangas = document.select(".search-typeDesc-listItem").map {
            SManga.create().apply {
                val link = it.selectFirst("a.search-typeDesc-titleLink")!!
                this.url = link.absUrl("href").toHttpUrl().pathSegments[4]
                title = link.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        val pager = document.selectFirst("#ja-search-Pn-wrap")
        val hasNextPage = pager != null && pager.attr("data-now") != pager.attr("data-max")
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = SManga.create().apply {
            title = document.selectFirst("h1.fvSeries-desc-title")!!.text()
            author = document.select("#js-productDetailsSection [data-schema-attribute=author] a").joinToString { it.text() }
            description = document.selectFirst("[data-text-type=textViewMore_toggle]")?.textOrNull()
            genre = document.select(".item-tags_wrap a:not([href*=price])").joinToString { it.text().replace('_', ' ') }
            status = if (document.selectFirst(".item-tags_wrap a[href$=\"keyword=Completed\"]") != null) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = document.selectFirst("img.fvSeries-cover-image")?.absUrl("src")
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val script = document.selectFirst("script:containsData(ItemStore.set)")!!.data()
        val items = script.substringAfter("JSON.parse(`").substringBefore("`);")
            .replace(UNESCAPE_REGEX, "$1")
            .parseAs<Map<String, Item>>()

        val chapterList = items.values
            .filter { !it.isFuture && !(hideLocked && it.isLocked) }
            .map { it.toSChapter() }
            .reversed()

        return SMangaUpdate(
            details,
            chapterList,
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/renta/sc/frm/item/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/renta/sc/jump/viewer?type=${chapter.memo["type"]!!.string}&prd_tid=9-${chapter.url}&style=ch"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        var document = client.get(getChapterUrl(chapter)).asJsoup()
        document.selectFirst("meta[http-equiv=refresh]")?.let {
            document = client.get(it.attr("content").substringAfter("URL=")).asJsoup()
        }
        if ("/view_strm/" in document.location()) throw Exception("AniComix videos are not supported!")

        val script = document.selectFirst("script:containsData(url_base2)")?.data()
            ?: throw Exception("Log in via WebView and purchase this chapter to read.")

        val vars = VIEWER_VAR_REGEX.findAll(script).associate { it.groupValues[1] to it.groupValues[2] }
        val imageUrl = vars["url_base2"]!!.toHttpUrl()
        return (1..vars["max_page"]!!.toInt()).map {
            val url = imageUrl.newBuilder()
                .addPathSegment(it.toString())
                .encodedQuery(vars["auth_key"]!!)
                .fragment(vars["prd_ser"]!!)
                .build()
            Page(it - 1, imageUrl = url.toString())
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val (genres, categories, keywords, deals, onSale) = FACETS.map { column ->
            async {
                val url = "$baseUrl/renta/sc/get_search_data_en.php".toHttpUrl().newBuilder()
                    .addQueryParameter("target_column", column)
                    .addQueryParameter("mode", "facet")
                    .build()
                client.get(url).parseAs<FacetResponse>().response
            }
        }.awaitAll()
        FilterData(genres, categories, keywords, deals, onSale).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = data?.parseAs<FilterData>() ?: return FilterList(SortFilter(), FormatFilter())
        return FilterList(
            SortFilter(),
            FormatFilter(),
            GenreFilter(filters.genres),
            CategoryFilter(filters.categories),
            KeywordFilter(filters.keywords),
            DealsFilter(filters.deals),
            OnSaleFilter(filters.onSale),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private val UNESCAPE_REGEX = Regex("""\\(.)""")
        private val VIEWER_VAR_REGEX = Regex("""(\w+)\s*=\s*(?:parseInt\()?"([^"]*)"""")
    }
}
