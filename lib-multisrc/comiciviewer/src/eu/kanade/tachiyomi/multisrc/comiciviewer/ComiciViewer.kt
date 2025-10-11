package eu.kanade.tachiyomi.multisrc.comiciviewer

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

abstract class ComiciViewer(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ConfigurableSource, HttpSource() {
    private val preferences: SharedPreferences by getPreferencesLazy()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)

    override val supportsLatest = true

    override val client = super.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking/manga", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.ranking-box-vertical, div.ranking-box-vertical-top3").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                title = element.selectFirst(".title-text")!!.text()
                thumbnail_url = element.selectFirst("source")?.attr("data-srcset")?.substringBefore(" ")?.let { "https:$it" }
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/category/manga", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.category-box-vertical").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                title = element.selectFirst(".title-text")!!.text()
                thumbnail_url = element.selectFirst("source")?.attr("data-srcset")?.substringBefore(" ")?.let { "https:$it" }
            }
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("page", (page - 1).toString())
                .addQueryParameter("filter", "series")
                .build()
            return GET(url, headers)
        }
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val browseFilter = filterList.firstInstance<BrowseFilter>()
        val pathAndQuery = getFilterOptions()[browseFilter.state].second
        val url = (baseUrl + pathAndQuery).toHttpUrl().newBuilder().build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url.toString()

        return when {
            url.contains("/ranking/") -> popularMangaParse(response)
            url.contains("/category/") -> latestUpdatesParse(response)

            else -> {
                val document = response.asJsoup()
                val mangas = document.select("div.manga-store-item").map { element ->
                    SManga.create().apply {
                        setUrlWithoutDomain(
                            element.selectFirst("a.c-ms-clk-article")!!.attr("href"),
                        )
                        title = element.selectFirst("h2.manga-title")!!.text()
                        thumbnail_url =
                            element.selectFirst("source")?.attr("data-srcset")?.substringBefore(" ")
                                ?.let { "https:$it" }
                    }
                }
                val hasNextPage = document.selectFirst("li.mode-paging-active + li > a") != null
                return MangasPage(mangas, hasNextPage)
            }
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.select("h1.series-h-title span").last()!!.text()
            author = document.select("div.series-h-credit-user").text()
            artist = author
            description = document.selectFirst("div.series-h-credit-info-text-text")?.text()
            genre = document.select("a.series-h-tag-link").joinToString { it.text().removePrefix("#") }
            thumbnail_url = document.selectFirst("div.series-h-img source")?.attr("data-srcset")?.substringBefore(" ")?.let { "https:$it" }
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url + "/list?s=1", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val showLocked = preferences.getBoolean(SHOW_LOCKED_PREF_KEY, true)
        val document = response.asJsoup()

        return document.select("div.series-ep-list-item").mapNotNull { element ->
            val link = element.selectFirst("a.g-episode-link-wrapper")!!

            val isFree = element.selectFirst("span.free-icon-new") != null
            val isTicketLocked = element.selectFirst("img[data-src*='free_charge_ja.svg']") != null
            val isCoinLocked = element.selectFirst("img[data-src*='coin.svg']") != null
            val isLocked = !isFree

            if (!showLocked && isLocked) {
                return@mapNotNull null
            }

            SChapter.create().apply {
                val chapterUrl = link.attr("data-href")
                if (chapterUrl.isNotEmpty()) {
                    setUrlWithoutDomain(chapterUrl)
                } else {
                    url = response.request.url.toString() + "#" + link.attr("data-article") + DUMMY_URL_SUFFIX
                }

                name = link.selectFirst("span.series-ep-list-item-h-text")!!.text()
                when {
                    isTicketLocked -> name = "🔒 $name"
                    isCoinLocked -> name = "\uD83E\uDE99 $name"
                }

                date_upload = dateFormat.tryParse(element.selectFirst("time")?.attr("datetime"))
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.endsWith(DUMMY_URL_SUFFIX)) {
            throw Exception("Log in via WebView to read purchased chapters and refresh the entry")
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val viewer = document.selectFirst("#comici-viewer") ?: throw Exception("You need to log in via WebView to read this chapter or purchase this chapter")
        val comiciViewerId = viewer.attr("comici-viewer-id")
        val memberJwt = viewer.attr("data-member-jwt")
        val requestUrl = "$baseUrl/book/contentsInfo".toHttpUrl().newBuilder()
            .addQueryParameter("comici-viewer-id", comiciViewerId)
            .addQueryParameter("user-id", memberJwt)
            .addQueryParameter("page-from", "0")

        val pageTo = client.newCall(GET(requestUrl.addQueryParameter("page-to", "1").build(), headers))
            .execute().use { initialResponse ->
                if (!initialResponse.isSuccessful) {
                    throw Exception("Failed to get page list")
                }
                initialResponse.parseAs<ViewerResponse>().totalPages.toString()
            }

        val getAllPagesUrl = requestUrl.setQueryParameter("page-to", pageTo).build()
        return client.newCall(GET(getAllPagesUrl, headers)).execute().use { allPagesResponse ->
            if (allPagesResponse.isSuccessful) {
                allPagesResponse.parseAs<ViewerResponse>().result.map { resultItem ->
                    val urlBuilder = resultItem.imageUrl.toHttpUrl().newBuilder()
                    if (resultItem.scramble.isNotEmpty()) {
                        urlBuilder.addQueryParameter("scramble", resultItem.scramble)
                    }
                    Page(
                        index = resultItem.sort,
                        imageUrl = urlBuilder.build().toString(),
                    )
                }.sortedBy { it.index }
            } else {
                throw Exception("Failed to get full page list")
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_PREF_KEY
            title = "Show locked chapters"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    protected open class BrowseFilter(vals: Array<String>) : Filter.Select<String>("Filter by", vals)

    protected open fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("読み切り", "/category/manga?type=読み切り"),
        Pair("完結", "/category/manga?type=完結"),
        Pair("月曜日", "/category/manga?type=連載中&day=月"),
        Pair("火曜日", "/category/manga?type=連載中&day=火"),
        Pair("水曜日", "/category/manga?type=連載中&day=水"),
        Pair("木曜日", "/category/manga?type=連載中&day=木"),
        Pair("金曜日", "/category/manga?type=連載中&day=金"),
        Pair("土曜日", "/category/manga?type=連載中&day=土"),
        Pair("日曜日", "/category/manga?type=連載中&day=日"),
        Pair("その他", "/category/manga?type=連載中&day=その他"),
    )

    override fun getFilterList() = FilterList(
        BrowseFilter(getFilterOptions().map { it.first }.toTypedArray()),
    )

    // Unsupported
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private const val SHOW_LOCKED_PREF_KEY = "pref_show_locked_chapters"
        private const val DUMMY_URL_SUFFIX = "NeedLogin"
    }
}
