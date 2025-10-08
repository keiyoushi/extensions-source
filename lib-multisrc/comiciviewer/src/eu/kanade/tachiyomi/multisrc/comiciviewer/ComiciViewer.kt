package eu.kanade.tachiyomi.multisrc.comiciviewer

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
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
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.manga-store-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.c-ms-clk-article")!!.attr("href"))
                title = element.selectFirst("h2.manga-title")!!.text()
                thumbnail_url = element.selectFirst("source")?.attr("data-srcset")?.substringBefore(" ")?.let { "https:$it" }
            }
        }
        val hasNextPage = document.selectFirst("li.mode-paging-active + li > a") != null
        return MangasPage(mangas, hasNextPage)
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

    override fun chapterListParse(response: Response): List<SChapter> {
        val showLocked = preferences.getBoolean(SHOW_LOCKED_PREF_KEY, true)
        val listUrl = response.request.url.toString() + "/list?s=1"
        val listResponse = client.newCall(GET(listUrl, headers)).execute()
        val document = listResponse.asJsoup()

        val link = document.select(".article-ep-list-item-img-link")
        if (link.isEmpty()) {
            return link.mapIndexed { index, element ->
                val hasLockElement = element.selectFirst(".g-payment-article.wait-free-enabled")
                if (!showLocked && hasLockElement != null) {
                    null
                } else {
                    SChapter.create().apply {
                        chapter_number = index.toFloat()
                        url = response.request.url.newBuilder().fragment("$index-$DUMMY_URL_SUFFIX").build().toString()
                        name = (if (hasLockElement != null) "🔒 " else "\uD83E\uDE99 ") + name
                    }
                }
            }.filterNotNull()
        } else {
            return document.select("div.series-ep-list a[data-href]").mapIndexed { index, element ->
                SChapter.create().apply {
                    chapter_number = index.toFloat()
                    setUrlWithoutDomain(element.attr("data-href"))
                    name = element.selectFirst("span.series-ep-list-item-h-text")!!.text()
                    date_upload = dateFormat.tryParse(element.selectFirst("time")?.attr("datetime"))
                }
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.endsWith(DUMMY_URL_SUFFIX)) {
            throw Exception("This chapter is locked. Log in via WebView to read if you have purchased this chapter.")
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pageList = mutableListOf<Page>()
        val viewer = document.selectFirst("#comici-viewer")!!
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
        client.newCall(GET(getAllPagesUrl, headers)).execute().use { allPagesResponse ->
            if (!allPagesResponse.isSuccessful) {
                throw Exception("Failed to get full page list")
            }

            allPagesResponse.parseAs<ViewerResponse>().result.forEach { resultItem ->
                val urlBuilder = resultItem.imageUrl.toHttpUrl().newBuilder()
                if (resultItem.scramble.isNotEmpty()) {
                    urlBuilder.addQueryParameter("scramble", resultItem.scramble)
                }

                pageList.add(
                    Page(index = resultItem.sort, imageUrl = urlBuilder.build().toString()),
                )
            }
        }

        return pageList
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_PREF_KEY
            title = "Show locked chapters"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    // Unsupported
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private const val SHOW_LOCKED_PREF_KEY = "pref_show_locked_chapters"
        private const val DUMMY_URL_SUFFIX = "NeedLogin"
    }
}
