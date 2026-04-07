package eu.kanade.tachiyomi.extension.ja.firecross

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.clipstudioreader.ClipStudioReader
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class FireCross :
    ClipStudioReader(),
    ConfigurableSource {
    override val name = "FireCross"
    override val baseUrl = "https://firecross.jp"
    override val lang = "ja"
    override val supportsLatest = false

    private val apiUrl = "$baseUrl/api"
    private val dateFormat = SimpleDateFormat("yyyy/M/d", Locale.ROOT)
    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ebook/comics?sort=1&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.seriesList li.seriesList_item").map {
            SManga.create().apply {
                val list = it.selectFirst("a.seriesList_itemTitle")!!
                setUrlWithoutDomain(list.absUrl("href"))
                title = list.text()
                thumbnail_url = it.selectFirst("img.series-list-img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a.pagination-btn--next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            addQueryParameter("t", "1")
            addQueryParameter("distribution_episode", "1")
            addQueryParameter("page", page.toString())

            filters.firstInstance<LabelFilter>().state.forEach { label ->
                if (label.state) {
                    addQueryParameter("label[]", label.value)
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.seriesList#search-result li.seriesList_item").map { element ->
            SManga.create().apply {
                title = element.selectFirst("a.seriesList_itemTitle")!!.text()
                thumbnail_url = element.selectFirst("img.series-list-img")?.absUrl("src")
                val webReadLink = element.select("a.btn-search-result").find { it.text() == "WEB読み" }
                setUrlWithoutDomain(webReadLink!!.absUrl("href"))
            }
        }
        val hasNextPage = document.selectFirst("nav.pagination a.pagination-btn--next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.ebook-series-title")!!.text()
            author = document.select("ul.ebook-series-author li").joinToString { it.text() }
            description = document.selectFirst("p.ebook-series-synopsis")?.text()
            genre = document.select("div.book-genre a").joinToString { it.text() }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapters = mutableListOf<SChapter>()
        var page = 1

        while (true) {
            val url = (baseUrl + manga.url).toHttpUrl().newBuilder()
                .addQueryParameter("sort", "latest")
                .addQueryParameter("page", page.toString())
                .build()
            val document = client.newCall(GET(url, headers)).execute().asJsoup()

            chapters += document.select("div.shop-item--episode").mapNotNull {
                val info = it.selectFirst(".shop-item-info")!!
                val nameText = info.selectFirst("span.shop-item-info-name")?.text()!!
                val dateText = info.selectFirst("span.shop-item-info-release")?.text()?.substringAfter("公開：")
                val form = it.selectFirst("form[data-api=reader]")

                SChapter.create().apply {
                    name = nameText
                    date_upload = dateFormat.tryParse(dateText)

                    when {
                        form != null -> {
                            val token = form.selectFirst("input[name=_token]")!!.attr("value")
                            val ebookId = form.selectFirst("input[name=ebook_id]")!!.attr("value")
                            this.url = ChapterId(token, ebookId).toJsonString()
                        }

                        else -> {
                            if (hideLocked) return@mapNotNull null
                            name = "🔒 $nameText"
                            val rentalId = it.attr("data-id")
                            this.url = "rental/$rentalId"
                        }
                    }
                }
            }

            val hasNextPage = document.selectFirst("li.ebookSeries_paginationLink.active ~ li.ebookSeries_paginationLink") != null
            if (!hasNextPage) break
            page++
        }

        return Observable.just(chapters)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        if (!chapter.url.startsWith("{")) {
            return Observable.error(Exception("Log in via WebView and purchase this chapter to read."))
        }

        val chapterId = chapter.url.parseAs<ChapterId>()

        val formBody = FormBody.Builder()
            .add("_token", chapterId.token)
            .add("ebook_id", chapterId.id)
            .build()

        val apiHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val apiRequest = POST("$apiUrl/reader", apiHeaders, formBody)

        return client.newCall(apiRequest).asObservable().map {
            val redirectUrl = it.parseAs<ApiResponse>().redirect
            val viewerRequest = GET(redirectUrl, headers)
            val viewerResponse = client.newCall(viewerRequest).execute()
            super.pageListParse(viewerResponse)
        }
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        Filter.Header("Note: Novels only show images, not text!"),
        LabelFilter(),
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

    // Unsupported
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
}
