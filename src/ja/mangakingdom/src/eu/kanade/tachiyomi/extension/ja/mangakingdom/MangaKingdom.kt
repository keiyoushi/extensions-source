package eu.kanade.tachiyomi.extension.ja.mangakingdom

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.annotation.Source
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException

@Source
abstract class MangaKingdom :
    HttpSource(),
    ConfigurableSource {
    override val supportsLatest = true

    private val viewerUrl = "https://bv.k-manga.jp/public/app/action/bd00.php"
    private val preferences by getPreferencesLazy()
    private val desktopHeaders = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36")
        .build()

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addNetworkInterceptor(CookieInterceptor(baseUrl.toHttpUrl().host, "is_verified_age_over_18" to "1"))
        .addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (!response.isSuccessful && request.url.fragment == "1") {
                throw IOException("Log in via WebView and purchase this product to read.")
            }
            response
        }
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/rank/", desktopHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".book-list-ranking .book-list--item").map(::mangaFromElement)
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/search/new/".toHttpUrl().newBuilder()
            .addQueryParameter("search_option[category]", "0")
            .addQueryParameter("search_option[new]", "0")
            .addQueryParameter("search_option[pvfv_flag]", "0")
            .addQueryParameter("search_option[finished_flag]", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, desktopHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".book-list__new .book-list--item").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".paging--next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        fun Builder.addFilter(param: String, filter: Filter.Text) = filter.state.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }
        fun Builder.addFilter(param: String, filter: SelectFilter) = filter.value.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }
        fun Builder.addFilter(param: String, filter: Filter.CheckBox) = filter.state.takeIf { it }?.let { addQueryParameter(param, "1") }
        fun Builder.addFilter(param: String, filter: Filter.Group<CheckBoxItem>) = filter.state.filter { it.state }.forEach { addQueryParameter(param, it.id) }

        val url = "$baseUrl/search/detail".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("search_option[search_word]", query)
            addFilter("search_option[sort]", filters.firstInstance<SortFilter>())
            addFilter("search_option[categories][]", filters.firstInstance<CategoryFilter>())
            addFilter("search_option[genres][]", filters.firstInstance<GenreFilter>())
            addFilter("search_option[keywords][]", filters.firstInstance<KeywordFilter>())
            addFilter("search_option[magazines][]", filters.firstInstance<MagazineFilter>())
            addFilter("search_option[finished_flag]", filters.firstInstance<FinishedFlagFilter>())
            addFilter("search_option[free_campaign_type]", filters.firstInstance<FreeCampaignFilter>())
            addFilter("search_option[discount_chapter][]", filters.firstInstance<DiscountFilter>())
            addFilter("search_option[without_sexy_title][]", filters.firstInstance<WithoutSexyTitleFilter>())
            addFilter("search_option[mangarepo_num]", filters.firstInstance<MangaRepoNumFilter>())
            addFilter("search_option[pvfv_flag]", filters.firstInstance<DistributionFilter>())
            addFilter("search_option[point_fv_max]", filters.firstInstance<PointFvMaxFilter>())
            addFilter("search_option[point_pv_max]", filters.firstInstance<PointPvMaxFilter>())
            addFilter("search_option[volume_fv_min]", filters.firstInstance<VolumeFvMinFilter>())
            addFilter("search_option[volume_fv_max]", filters.firstInstance<VolumeFvMaxFilter>())
            addFilter("search_option[volume_pv_min]", filters.firstInstance<VolumePvMinFilter>())
            addFilter("search_option[volume_pv_max]", filters.firstInstance<VolumePvMaxFilter>())
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return GET(url, desktopHeaders)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        SortFilter(),
        CategoryFilter(),
        GenreFilter(),
        KeywordFilter(),
        MagazineFilter(),
        FinishedFlagFilter(),
        FreeCampaignFilter(),
        DiscountFilter(),
        WithoutSexyTitleFilter(),
        MangaRepoNumFilter(),
        DistributionFilter(),
        Filter.Separator(),
        Filter.Header("価格 (空欄で無指定 / pt)"),
        PointFvMaxFilter(),
        PointPvMaxFilter(),
        Filter.Separator(),
        Filter.Header("配信巻数・話数 (空欄で無指定)"),
        VolumeFvMinFilter(),
        VolumeFvMaxFilter(),
        VolumePvMinFilter(),
        VolumePvMaxFilter(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".book-list-detail--box a.book-list--item").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".paging--next") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val id = element.absUrl("href").toHttpUrl().pathSegments[1]
        setUrlWithoutDomain(id)
        title = element.selectFirst(".book-list--title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}/pv"

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), desktopHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.book-info--title span[itemprop=name]")!!.text()
            author = document.selectFirst(".book-info--detail dt:contains(著者・作者) + dd")?.select("a")?.joinToString { it.ownText() }
            description = document.selectFirst("p.book-info--desc-text")?.text()
            genre = document.selectFirst(".book-info--detail dt:contains(ジャンル) + dd")?.select("a")?.joinToString { it.text() }
            val completed = document.selectFirst(".book-info--detail dt:contains(配信) + dd")?.text()?.contains("完結") == true
            status = if (completed) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = document.selectFirst("img.book-info--img")?.absUrl("src")
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapters = mutableListOf<SChapter>()
        var page = 1

        do {
            val response = client.newCall(GET("$baseUrl/title/${manga.url}/pv/$page", desktopHeaders)).execute()
            val document = response.asJsoup()
            val titleJs = document.getElementById("titlejs")
            val formatType = titleJs?.attr("data-format-type")?.takeIf { it.isNotEmpty() } ?: "pv"
            val viewerId = titleJs?.attr("data-viewer-id-pc")?.takeIf { it.isNotEmpty() } ?: "3"
            val title = document.selectFirst("h1.book-info--title span[itemprop=name]")!!.text()

            chapters += document.select("ul.book-chapter li.book-chapter--target").mapNotNull {
                val btn = it.selectFirst(".x-invoke-viewer--btn__selector")
                val isSample = btn?.hasClass("book-chapter--btn__sample") == true
                val isLocked = btn == null || isSample

                if (hideLocked && isLocked) return@mapNotNull null
                val prefix = when {
                    isSample -> "🔒 (Preview) "
                    isLocked -> "🔒 "
                    else -> ""
                }

                SChapter.create().apply {
                    val book = it.selectFirst("h2.book-chapter--title a")!!
                    val trimmedName = book.text().replace(title, "")
                    name = prefix + trimmedName

                    if (btn != null) {
                        val exid = btn.attr("data-chapter-exid")
                        val fcipath = btn.attr("data-chapter-fcipath")
                        val readType = btn.attr("data-chapter-readtype")
                        val fcid = btn.attr("data-chapter-fcid")
                        val fcupdated = btn.attr("data-chapter-fcupdated")

                        setUrlWithoutDomain("$viewerId/1/${manga.url}/$formatType/$exid/$fcipath/$readType/$fcid/$fcupdated")
                    } else {
                        setUrlWithoutDomain(book.absUrl("href") + "#1")
                    }
                }
            }

            val hasNextPage = document.selectFirst(".paging--next") != null
            page++
        } while (hasNextPage)

        return Observable.just(chapters.reversed())
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/viewer-launcher/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url
        response.close()
        val ticket = url.queryParameter("p0")!!
        val obfuid = url.queryParameter("p1")!!
        val headerUrl = buildViewerUrl(ticket, "64kb_QVGA_h", obfuid, "header")
        val header = client.newCall(GET(headerUrl, desktopHeaders)).execute().parseAs<HeaderResponse> { stripJson(it) }
        return header.contentInfos.flatMap {
            (it.startSceneNo..it.endSceneNo).map { sceneNo ->
                val pageUrl = buildViewerUrl(ticket, it.name, obfuid, "content", header.dk).newBuilder()
                    .fragment("scene=$sceneNo")
                    .build()
                    .toString()
                pageUrl
            }
        }.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    private fun buildViewerUrl(t: String, fn: String, o: String, type: String, dk: String? = null): HttpUrl = viewerUrl.toHttpUrl().newBuilder()
        .addQueryParameter("t", t)
        .addQueryParameter("fn", fn)
        .addQueryParameter("o", o)
        .addQueryParameter("type", type)
        .addQueryParameter("callback", "cb")
        .addQueryParameter("u", System.currentTimeMillis().toString())
        .apply { if (dk != null) addQueryParameter("dk", dk) }
        .build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"

        internal fun stripJson(s: String): String {
            val trimmed = s.trim()
            val open = trimmed.indexOf('(')
            val close = trimmed.lastIndexOf(')')
            return if (open in 0 until close) trimmed.substring(open + 1, close) else trimmed
        }
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
