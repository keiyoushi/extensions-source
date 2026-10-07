package eu.kanade.tachiyomi.extension.ja.firecross

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class FireCross :
    KeiSource(),
    ConfigurableSource {
    override val supportsLatest = false

    private val apiUrl get() = "$baseUrl/api"
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ROOT).withZone(ZoneId.of("Asia/Tokyo"))
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ClipStudioReaderInterceptor())

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
            filters.firstInstance<LabelFilter>().state.filter { it.state }.forEach { addQueryParameter("label[]", it.value) }
        }.build()

        val document = client.get(url).asJsoup()
        val mangas = document.select("ul.seriesList#search-result li.seriesList_item").map {
            SManga.create().apply {
                title = it.selectFirst("a.seriesList_itemTitle")!!.text()
                thumbnail_url = it.selectFirst("img.series-list-img")?.absUrl("src")
                setUrlWithoutDomain(it.selectFirst("a.btn-search-result[href*=/ebook/series/]")!!.absUrl("href"))
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
        val seriesUrl = (baseUrl + manga.url).toHttpUrl().newBuilder().addQueryParameter("sort", "latest").build()
        val firstPage = client.get(seriesUrl).asJsoup()
        val details = parseDetails(firstPage)

        if (!fetchChapters) return@coroutineScope SMangaUpdate(details, chapters)

        val lastPage = firstPage.select("ul.ebookSeries_paginationLinks a").mapNotNull {
            it.absUrl("href").toHttpUrl().queryParameter("page")?.toIntOrNull()
        }.maxOrNull() ?: 1

        val otherPages = (2..lastPage).map { page ->
            async { client.get(seriesUrl.newBuilder().addQueryParameter("page", page.toString()).build()).asJsoup() }
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = (listOf(firstPage) + otherPages.awaitAll()).flatMap {
            it.select("div.shop-item--episode").mapNotNull { element ->
                val token = element.selectFirst("form[data-api=reader] input[name=_token]")?.attr("value")
                val isLocked = token == null
                if (hideLocked && isLocked) return@mapNotNull null

                val title = element.selectFirst("span.shop-item-info-name")!!.text()
                val releaseDate = element.selectFirst("span.shop-item-info-release")?.textOrNull()?.substringAfter("公開：")
                SChapter.create().apply {
                    url = element.attr("data-id")
                    name = if (isLocked) "🔒 $title" else title
                    date_upload = dateFormat.tryParseDate(releaseDate)
                    memo = buildJsonObject {
                        if (isLocked) put("locked", true) else put("token", token)
                    }
                }
            }
        }

        SMangaUpdate(
            details,
            chapterList,
        )
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.ebook-series-title")!!.text()
        author = document.select("ul.ebook-series-author li").joinToString { it.text() }
        description = document.selectFirst("p.ebook-series-synopsis")?.textOrNull()
        genre = document.select("div.book-genre a").joinToString { it.text() }
        thumbnail_url = document.selectFirst("img.ebook-series-img")?.absUrl("src")
    }

    override fun getChapterUrl(chapter: SChapter): String = runBlocking { fetchReaderUrl(chapter) }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.fetchPages(client.get(fetchReaderUrl(chapter)).asJsoup())

    private suspend fun fetchReaderUrl(chapter: SChapter): String {
        if ("locked" in chapter.memo) throw Exception("Log in via WebView and purchase this chapter to read.")

        val apiHeaders = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        val body = FormBody.Builder()
            .add("_token", chapter.memo["token"]!!.string)
            .add("ebook_id", chapter.url)
            .build()

        val response = client.post("$apiUrl/reader", apiHeaders, body, ensureSuccess = false)
        if (response.code == 419) {
            response.close()
            throw Exception("Refresh the chapter list.")
        }

        return response.parseAs<ApiResponse>().redirect
    }

    @Serializable
    class ApiResponse(
        val redirect: String,
    )

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
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
