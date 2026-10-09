package eu.kanade.tachiyomi.extension.ja.sugocomics

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
import keiyoushi.lib.clipstudioreader.ClipStudioReaderInterceptor
import keiyoushi.lib.clipstudioreader.fetchPages
import keiyoushi.network.get
import keiyoushi.network.head
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class SugoComics :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ClipStudioReaderInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseUrl/products/list.php?mode=week_series_ranking&group_id=1").toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/products/list.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("name", query)
            addFilter("orderby", filters.firstInstanceOrNull<SortFilter>())
            addFilter("category_id", filters.firstInstanceOrNull<CategoryFilter>())
            if (filters.firstInstanceOrNull<FreeFilter>()?.state == true) addQueryParameter("free_type", "free")
            addQueryParameter("pageno", page.toString())
        }.build()
        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select("li.product_list-book-item").map {
            SManga.create().apply {
                url = it.selectFirst("input[name=product_id]")!!.attr("value")
                title = it.selectFirst(".top-book-title")!!.text().removeSuffix(" (無料)")
                thumbnail_url = it.selectFirst("img.book_cover")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("ul.result-pager li.active + li") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val seriesUrl = "$baseUrl/products/detail.php".toHttpUrl().newBuilder()
            .addQueryParameter("product_id", manga.url)
            .addQueryParameter("disp_number", "500")
            .build()

        val firstPage = client.get(seriesUrl).asJsoup()
        val info = firstPage.select("ul.product_details-main-content-list li").associate {
            it.selectFirst(".detail-name")!!.text() to it.selectFirst(".detail-content")?.textOrNull()
        }
        val details = SManga.create().apply {
            title = info["シリーズ"] ?: firstPage.selectFirst("h1.product_details-title")!!.text()
            author = info["著者"]
            description = firstPage.selectFirst("p.product_details-area-text")?.textOrNull()
            genre = listOfNotNull(info["カテゴリー"], info["ジャンル"]).joinToString()
            thumbnail_url = firstPage.selectFirst("img.product_details-main-content-img")?.absUrl("src")
        }

        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val pages = mutableListOf(firstPage)
        while (pages.last().selectFirst("ul.result-pager li.active + li") != null) {
            pages += client.get(seriesUrl.newBuilder().addQueryParameter("pageno", (pages.size + 1).toString()).build()).asJsoup()
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = pages.flatMap {
            it.select("#product_details-series-list > li").mapNotNull { element ->
                val isLocked = element.selectFirst(".product_details-btn-box button[onclick*=reading]") == null
                if (hideLocked && isLocked) return@mapNotNull null
                val title = element.selectFirst("h2.product_details-series-title")!!.text()
                SChapter.create().apply {
                    url = element.selectFirst("a.product_details-series-content-img-box")!!.absUrl("href").toHttpUrl().queryParameter("product_id")!!
                    name = if (isLocked) "🔒 $title" else title
                }
            }
        }

        return SMangaUpdate(
            details,
            chapterList.reversed(),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/products/detail.php?product_id=${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/products/reading.php?product_id=${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val viewerUrl = client.head(getChapterUrl(chapter)).use { it.request.url }
        if (viewerUrl.queryParameter("cgi") == null) throw Exception("Log in via WebView and purchase this product to read.")
        return client.fetchPages(viewerUrl)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        CategoryFilter(),
        FreeFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
