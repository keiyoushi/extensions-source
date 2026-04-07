package eu.kanade.tachiyomi.extension.ja.dmm

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
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.fetchPages
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

abstract class Dmm :
    HttpSource(),
    ConfigurableSource {
    protected abstract val domain: String
    protected abstract val shopName: String

    override val baseUrl get() = "https://$domain"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl get() = "$baseUrl/ajax/bff"
    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .addInterceptor(PublusInterceptor())
            .addNetworkInterceptor(CookieInterceptor(domain, listOf("book_safe_mode_level" to "off", "age_check_done" to "1")))
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (request.url.fragment == "locked") {
                    throw IOException("Log in via WebView and purchase this product to read.")
                }
                val path = response.request.url.encodedPath
                if (path.contains("/service/login/password/") || path == "/shelf/") {
                    throw IOException("Your country is not supported.")
                }
                response
            }
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val desktopHeaders by lazy {
        headersBuilder()
            .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36")
            .build()
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/list/".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", "ranking")
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()
        return GET(url, desktopHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".m-boxListBookProduct2__item").map {
            SManga.create().apply {
                title = it.selectFirst(".m-boxListBookProduct2Tmb__ttl")!!.text().replace(TITLE_REGEX, "")
                val thumbUrl = it.selectFirst("img.m-bookImage__img")?.absUrl("src")
                thumbnail_url = thumbUrl?.replace(THUMBNAIL_REGEX, "l")
                val id = it.selectFirst("a[href*=/product/]")!!.absUrl("href").toHttpUrl().pathSegments[1]
                setUrlWithoutDomain(id)
            }
        }

        val hasNextPage = document.selectFirst("a.m-boxPaging:contains(>)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/list/".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", "date")
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()
        return GET(url, desktopHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("searchstr", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, desktopHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".m-boxSearchBookProduct__item").map {
            SManga.create().apply {
                title = it.selectFirst(".m-boxSearchListTmb__ttl")!!.text().replace(TITLE_REGEX, "")
                val thumbUrl = it.selectFirst("img.m-bookImage__img")?.absUrl("src")
                thumbnail_url = thumbUrl?.replace(THUMBNAIL_REGEX, "l")
                val id = it.selectFirst("a[href*=/product/]")!!.absUrl("href").toHttpUrl().pathSegments[1]
                setUrlWithoutDomain(id)
            }
        }

        val hasNextPage = document.selectFirst("a.m-boxPaging:contains(>)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/contents_book/".toHttpUrl().newBuilder()
            .addQueryParameter("shop_name", shopName)
            .addQueryParameter("series_id", manga.url)
            .addQueryParameter("format_webp", "1")
            .addQueryParameter("order", "desc")
            .addQueryParameter("purchase_status", "all")
            .addQueryParameter("page", "1")
            .addQueryParameter("per_page", "1")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val contentId = response.parseAs<ChapterResponse>().volumeBooks.first().contentId
        val seriesId = response.request.url.queryParameter("series_id")
        val detailsUrl = "$apiUrl/product_volume/".toHttpUrl().newBuilder()
            .addQueryParameter("shop_name", shopName)
            .addQueryParameter("series_id", seriesId)
            .addQueryParameter("content_id", contentId)
            .addQueryParameter("format_webp", "1")
            .build()
        val detailsResponse = client.newCall(GET(detailsUrl, headers)).execute()
        return detailsResponse.parseAs<DetailsResponse>().toSManga()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/product/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$apiUrl/contents_book/".toHttpUrl().newBuilder()
            .addQueryParameter("shop_name", shopName)
            .addQueryParameter("series_id", manga.url)
            .addQueryParameter("format_webp", "1")
            .addQueryParameter("order", "desc")
            .addQueryParameter("purchase_status", "all")
            .addQueryParameter("page", "1")
            .addQueryParameter("per_page", "100")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        return response.parseAs<ChapterResponse>().volumeBooks
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter(baseUrl) }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val cid = response.request.url.queryParameter("cid")
        val cUrl = "$baseUrl/viewerapi/auth/".toHttpUrl().newBuilder()
            .addQueryParameter("cid", cid)
            .build()

        val cRequest = GET(cUrl, headers)
        val cResponse = client.newCall(cRequest).execute()
        val contentPhp = cResponse.parseAs<CPhpResponse>().url

        return fetchPages(contentPhp, headers, client)
    }

    override fun imageUrlParse(response: Response): String = response.request.url.toString()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private val THUMBNAIL_REGEX = Regex(".(?=\\.\\w+$)")
        private val TITLE_REGEX = Regex("(?:(?<=\\s|】)(第?\\d+巻|第?\\d+話|\\d+(?=\\s*$))|（[０-９0-9]+）|【第?\\d+[巻話]】|#\\d+).*$")
    }
}
