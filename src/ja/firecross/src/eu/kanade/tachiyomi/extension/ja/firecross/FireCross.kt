package eu.kanade.tachiyomi.extension.ja.firecross

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.clipstudioreader.ClipStudioReader
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class FireCross :
    ClipStudioReader(),
    ConfigurableSource {

    override val supportsLatest = false

    private val apiUrl get() = "$baseUrl/api"
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ROOT)
    private val preferences: SharedPreferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/ebook/comics?sort=1&page=$page").asJsoup()
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

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        val document = client.get(url).asJsoup()
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

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.startsWith("/ebook/series/")) return null

        return parseDetails(client.get(url).asJsoup()).apply { this.url = url.encodedPath }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) parseDetails(client.get(getMangaUrl(manga)).asJsoup()) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.ebook-series-title")!!.text()
        author = document.select("ul.ebook-series-author li").joinToString { it.text() }
        description = document.selectFirst("p.ebook-series-synopsis")?.text()
        genre = document.select("div.book-genre a").joinToString { it.text() }
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapters = mutableListOf<SChapter>()
        var page = 1

        while (true) {
            val url = (baseUrl + manga.url).toHttpUrl().newBuilder()
                .addQueryParameter("sort", "latest")
                .addQueryParameter("page", page.toString())
                .build()
            val document = client.get(url).asJsoup()

            chapters += document.select("div.shop-item--episode").mapNotNull {
                val info = it.selectFirst(".shop-item-info")!!
                val nameText = info.selectFirst("span.shop-item-info-name")?.text()!!
                val dateText = info.selectFirst("span.shop-item-info-release")?.text()?.substringAfter("公開：")
                val form = it.selectFirst("form[data-api=reader]")

                SChapter.create().apply {
                    name = nameText
                    date_upload = dateFormat.tryParseDate(dateText, ZoneId.of("Asia/Tokyo"))

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

        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (!chapter.url.startsWith("{")) {
            throw Exception("Log in via WebView and purchase this chapter to read.")
        }

        val chapterId = chapter.url.parseAs<ChapterId>()

        val formBody = FormBody.Builder()
            .add("_token", chapterId.token)
            .add("ebook_id", chapterId.id)
            .build()

        val apiHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val redirectUrl = client.post("$apiUrl/reader", apiHeaders, formBody).parseAs<ApiResponse>().redirect
        return pageListParse(client.get(redirectUrl))
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
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
}
