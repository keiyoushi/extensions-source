package eu.kanade.tachiyomi.extension.en.manhwaxxl

import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

class ManhwaXXL :
    HttpSource(),
    ConfigurableSource {

    override val name = "Manhwa XXL"
    override val lang = "en"
    override val baseUrl = "https://hentaitnt.net"
    override val supportsLatest = true
    override val versionId = 2

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/recommended" + (if (page > 1) "/page/$page" else ""))

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".comic-card a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.attr("title")
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a[title=Next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest" + (if (page > 1) "/page/$page" else ""))

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            } else {
                val genreFilter = filters.firstInstanceOrNull<Filters>()
                if (genreFilter != null) {
                    val genreId = genreFilter.selectedId
                    if (genreId.isNotEmpty()) {
                        addPathSegment("genre")
                        addPathSegment(genreId)
                    }
                }
            }
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            author = document.selectFirst("i[title=Artists] + span a")?.text()
            description = document.selectFirst("#synopsisText")?.text()
            genre = document.select(".genre-item").joinToString { it.text() }
            status = when (document.selectFirst("i[title=Status]")?.text()?.lowercase()) {
                "completed" -> SManga.COMPLETED
                "ongoing" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val detailsDocument = response.asJsoup()
        val mangaId = detailsDocument.selectFirst("#post_manga_id")?.attr("value")
            ?: throw Exception("Failed to get chapter id")

        val form = FormBody.Builder()
            .add("action", "baka_ajax")
            .add("type", "load_chapters_paginated")
            .add("parent_id", mangaId)
            .add("per_page", "10000")
            .add("order", "newest_first")
            .build()

        val ajaxResponse = client.newCall(
            POST("$baseUrl/wp-admin/admin-ajax.php", headers, form),
        ).execute()

        val dto = ajaxResponse.parseAs<Dto>()
        val chapterDoc = Jsoup.parseBodyFragment(dto.data.html, baseUrl)

        return chapterDoc.select(".comic-card").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null
            val isVip = element.selectFirst(".fa-crown") != null

            if (isVip && preferences.getBoolean(HIDE_VIP_PREF, false)) {
                return@mapNotNull null
            }

            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = (if (isVip) "🔒 " else "") + link.attr("title")
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".page-image").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Ignored if using text search"),
        Filters(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = HIDE_VIP_PREF
            title = "Hide VIP chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_VIP_PREF = "hide_vip_chapters"
    }
}
