package eu.kanade.tachiyomi.extension.ja.cmoa

import android.content.SharedPreferences
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
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.lib.speedbinb.SpeedBinbInterceptor
import keiyoushi.lib.speedbinb.SpeedBinbReader
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.jsonInstance
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class Cmoa :
    HttpSource(),
    ConfigurableSource {
    override val name = "C'moA"
    private val domain = "cmoa.jp"
    override val baseUrl = "https://www.$domain"
    override val lang = "ja"
    override val supportsLatest = true

    private val reader by lazy { SpeedBinbReader(client, headers, jsonInstance, true) } // 1.7070.1001 SBC
    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(SpeedBinbInterceptor(jsonInstance))
        .addNetworkInterceptor(CookieInterceptor(domain, listOf("safesearch" to "0", "R18user" to "1")))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        // load desktop selectors
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/search/purpose/ranking/all".toHttpUrl().newBuilder()
            .addQueryParameter("period", "daily")
            .addQueryParameter("daily", "all")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("li.search_result_box").map {
            SManga.create().apply {
                title = it.selectFirst("div.search_result_box_right_sec1 a.title")!!.text()
                val img = it.selectFirst("div.search_result_box_left img")
                thumbnail_url = img?.absUrl("data-src")?.ifEmpty { img.absUrl("src") }
                val id = it.selectFirst("div.search_result_box_left a.title")!!.absUrl("href").toHttpUrl().pathSegments[1]
                setUrlWithoutDomain(id)
            }
        }
        val hasNextPage = document.selectFirst("li.next:not(.nopage)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/newrelease/?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.title_list li.title_wrap").map {
            SManga.create().apply {
                title = it.selectFirst("div.text_box p.title_name")!!.text()
                thumbnail_url = it.selectFirst("div.thum_box a img")?.absUrl("src")
                val id = it.selectFirst("div.thum_box a")!!.absUrl("href").toHttpUrl().pathSegments[1]
                setUrlWithoutDomain(id)
            }
        }
        val hasNextPage = document.selectFirst("div.pageSlider div.swiper-slide.selected + div.swiper-slide") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        fun Builder.addFilter(param: String, filter: Filter.Text) = filter.state.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }
        fun Builder.addFilter(param: String, filter: SelectFilter) = filter.value.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }
        fun Builder.addFilter(param: String, filter: Filter.CheckBox) = filter.state.takeIf { it }?.let { addQueryParameter(param, "1") }

        val url = "$baseUrl/search/result".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("word", query)
            addFilter("title_nm", filters.firstInstance<TitleFilter>())
            addFilter("author_nm", filters.firstInstance<AuthorFilter>())
            addFilter("magazine_nm", filters.firstInstance<MagazineFilter>())
            addFilter("publisher_nm", filters.firstInstance<PublisherFilter>())
            addFilter("titletag_nm", filters.firstInstance<TitleTagFilter>())
            addFilter("genre_id", filters.firstInstance<GenreFilter>())
            addFilter("point", filters.firstInstance<PriceFilter>())
            addFilter("review", filters.firstInstance<ReviewFilter>())
            addFilter("sort", filters.firstInstance<SortFilter>())
            addFilter("free_cam_flg", filters.firstInstance<FreeFilter>())
            addFilter("sample_up_flg", filters.firstInstance<SampleFilter>())
            addFilter("campaign_flg", filters.firstInstance<CampaignFilter>())
            addFilter("newest_flg", filters.firstInstance<NewestFilter>())
            addFilter("complete_flg", filters.firstInstance<CompleteFilter>())
            addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.titleName")!!.text().replace(TITLE_REGEX, "")
            author = document.select("div.title_details_author_name a")?.joinToString { it.text() }
            description = document.selectFirst("div#comic_description > p")?.text()
            genre = buildList {
                document.selectFirst("a.comic_mark_thum")?.text()?.let { add(it) }
                document.select("div.category_line_f_r_l.genre_detail a")?.mapTo(this) { it.text() }
            }.joinToString()
            val completed = document.selectFirst("div.volume")?.text()?.contains("完結")
            status = if (completed == true) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = document.selectFirst("div.thumBox img")?.absUrl("src")
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapters = mutableListOf<SChapter>()
        var page = 1

        while (true) {
            val request = GET("$baseUrl/title/${manga.url}?page=$page&order=down", headers)
            val response = client.newCall(request).execute()
            val document = response.asJsoup()

            val mangaTitle = document.selectFirst("h1.titleName")!!.text().replace(TITLE_REGEX, "")

            chapters += document.select("ul.title_vol_vox_vols li").mapNotNull {
                SChapter.create().apply {
                    val chapterUrl = it.selectFirst("div.title_vol_btn_box_w a[href*=content_id]")
                        ?.absUrl("href")
                        ?: it.selectFirst("h3.title_details_title_name_h2 a")!!.absUrl("href")
                    setUrlWithoutDomain(chapterUrl)

                    val rawName = it.selectFirst("h3.title_details_title_name_h2 a")!!.text()
                        .replace(mangaTitle, "")
                        .replace("NEW ", "")
                        .replace("発売予定 ", "")

                    val hasFreeOrOwned = it.selectFirst("div.GA_free.btn, div.title_vol_btn_box_w a[href*=browserviewer]") != null
                    val hasPreview = !hasFreeOrOwned && it.selectFirst("div.title_vol_each_free_btn.GA_free") != null
                    val isLocked = !hasFreeOrOwned && it.selectFirst("div.title_vol_btn_box_w a.cart_into_btn, div.title_vol_btn_box_w a.auto_buy_btn_s") != null

                    if (hideLocked && (isLocked || hasPreview)) return@mapNotNull null

                    name = when {
                        hasFreeOrOwned -> rawName
                        hasPreview -> "🔒 (Preview) $rawName"
                        isLocked -> "🔒 $rawName"
                        else -> rawName
                    }
                }
            }

            val hasNextPage = document.selectFirst("div.pagination li.selected + li a") != null
            if (!hasNextPage) break
            page++
        }

        return Observable.just(chapters)
    }

    override fun pageListParse(response: Response): List<Page> {
        if (response.request.url.pathSegments.contains("speedreader")) {
            return reader.pageListParse(response)
        }

        if (response.request.url.encodedPath.contains("/bib/reader")) {
            throw Exception("Novels are not supported.")
        }
        throw Exception("Log in via WebView and purchase this product to read.")
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        Filter.Header("Note: Novels are not supported!"),
        TitleFilter(),
        AuthorFilter(),
        MagazineFilter(),
        PublisherFilter(),
        TitleTagFilter(),
        GenreFilter(),
        PriceFilter(),
        ReviewFilter(),
        SortFilter(),
        Filter.Separator(),
        Filter.Header("お得（複数選択可）"),
        FreeFilter(),
        SampleFilter(),
        CampaignFilter(),
        Filter.Separator(),
        Filter.Header("作品の条件（複数選択可）"),
        NewestFilter(),
        CompleteFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // Unsupported
    override fun chapterListRequest(manga: SManga) = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private val TITLE_REGEX = Regex("(?:(?<=\\s|】)(第?\\d+巻|第?\\d+話|\\d+(?=\\s*$))|（[０-９0-9]+）|【第?\\d+[巻話]】|#\\d+).*$")
    }
}
