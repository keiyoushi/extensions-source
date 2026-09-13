package eu.kanade.tachiyomi.extension.ja.alphapolis

import android.util.Base64
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
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.readUShortLittleEndian
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.net.URLDecoder

@Source
abstract class Alphapolis :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val desktopHeaders get() = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36")
        .build()

    override fun Headers.Builder.configureHeaders() = set("X-Requested-With", "XMLHttpRequest")

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code != 419) return@addInterceptor response
            response.close()
            val token = xsrfToken() ?: throw Exception("XSRF-Token not found")
            val newRequest = request.newBuilder()
                .header("X-XSRF-TOKEN", token)
                .build()
            it.proceed(newRequest)
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseUrl/manga/official/ranking?category=total", desktopHeaders).toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get("$baseUrl/manga/official/search?page=$page", desktopHeaders).toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        fun Builder.addFilter(param: String, filter: Filter.Group<FilterTag>) = filter.state.filter { it.state }.forEachIndexed { i, option -> addQueryParameter("$param[$i]", option.value) }
        fun Builder.addFilter(param: String, value: String, filter: Filter.CheckBox) = apply { if (filter.state) addQueryParameter(param, value) }

        val url = "$baseUrl/manga/official/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotBlank()) addQueryParameter("query", query)
                addFilter("category", filters.firstInstance<CategoryFilter>())
                addFilter("label", filters.firstInstance<LabelFilter>())
                addFilter("complete", filters.firstInstance<StatusFilter>())
                addFilter("rental", filters.firstInstance<RentalFilter>())
                addFilter("is_free_daily", "enable", filters.firstInstance<DailyFreeFilter>())
            }.build()
        return client.get(url, desktopHeaders).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select(".mangas-list .official-manga-panel > a, .official-manga-sub-like_ranking--list, .official-manga-sub-like_ranking--panel").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst(".title, .official-manga-sub-like_ranking--list_title, .official-manga-sub-like_ranking--panel_title")!!.text()
                val thumb = it.selectFirst(".panel, img, .official-manga-sub-like_ranking--panel_thumbnail")
                thumbnail_url = thumb?.absUrl("data-src")?.ifEmpty { thumb.absUrl("data-bg") }
            }
        }
        val hasNextPage = document.selectFirst("i.fa.fa-angle-double-right") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangas = async {
            if (!fetchDetails) return@async manga
            val document = client.get(getMangaUrl(manga), desktopHeaders).asJsoup()
            SManga.create().apply {
                title = document.selectFirst(".manga-detail-description > .title > h1")!!.text()
                val authors = document.select(".manga-detail-description .author-label .authors .mangaka").toList()
                author = authors.filter { it.text().contains("原作") }
                    .mapNotNull { it.selectFirst("a")?.text() }
                    .joinToString()
                artist = authors.filter { it.text().contains("漫画") }
                    .mapNotNull { it.selectFirst("a")?.text() }
                    .joinToString()
                description = document.selectFirst(".manga-detail-outline .outline")?.text()
                genre = document.select(".manga-detail-tags .official-manga-tags .official-manga-tag").joinToString { it.text() }
                status = when (document.selectFirst(".wrap-content-status a[href*=complete]")?.text()) {
                    "連載中" -> SManga.ONGOING
                    "完結" -> SManga.COMPLETED
                    "休載中" -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }
                thumbnail_url = document.selectFirst(".manga-bigbanner img")?.absUrl("src")
            }
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val mangaId = (baseUrl + manga.url).toHttpUrl().pathSegments.last().toInt()
            val body = ChapterRequestBody(mangaId).toJsonRequestBody()
            val result = client.post("$baseUrl/manga/official/episodes.json", xsrfHeaders(), body).parseAs<ChapterResponse>()
            val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
            result.episodes.filter { !hideLocked || !it.isLocked }.map { it.toSChapter(baseUrl) }.reversed()
        }

        SMangaUpdate(
            mangas.await(),
            chapterList.await(),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaId = chapter.memo["mangaId"]?.int ?: throw Exception("Refresh Chapter List")
        return "$baseUrl/manga/official/$mangaId/${chapter.url}"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangaId = chapter.memo["mangaId"]?.int ?: throw Exception("Refresh Chapter List")
        val episodeId = chapter.url.toInt()
        val viewerHeaders = xsrfHeaders(getChapterUrl(chapter))

        for (resolution in listOf("full_hd", "standard")) {
            val body = ViewerRequestBody(episodeId, false, mangaId, false, resolution).toJsonRequestBody()
            val result = client.post("$baseUrl/manga/official/viewer.json", viewerHeaders, body).parseAs<ViewerResponse>()
            val page = result.page ?: continue
            val keys = page.placeholder.extractKeys()
            val pages = page.images.mapIndexed { i, img ->
                val keyEncoded = Base64.encodeToString(keys[i], Base64.NO_WRAP)
                Page(i, imageUrl = "${img.url}#key=$keyEncoded")
            }

            if (pages.isNotEmpty()) return pages
        }

        throw Exception("Log in via WebView and rent or purchase this chapter to read.")
    }

    private fun String.extractKeys(): List<ByteArray> {
        val raw = Base64.decode(substringAfter("base64,"), Base64.DEFAULT)

        val keys = mutableListOf<ByteArray>()
        var pos = 33 // right after the PNG signature + IHDR chunk
        while (pos + 2 <= raw.size) {
            val count = raw.readUShortLittleEndian(pos)
            val length = count * 8
            val dataStart = pos + 2
            val dataEnd = dataStart + length
            if (dataEnd > raw.size) break
            keys.add(raw.copyOfRange(dataStart, dataEnd))
            pos = dataEnd
        }
        return keys
    }

    private fun xsrfHeaders(referer: String? = null) = headersBuilder()
        .set("X-XSRF-TOKEN", xsrfToken() ?: throw Exception("XSRF-Token not found"))
        .apply { if (referer != null) set("Referer", referer) }
        .build()

    private fun xsrfToken(): String? {
        val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        return cookies.firstOrNull { it.name == "XSRF-TOKEN" }?.value?.let {
            URLDecoder.decode(it, "UTF-8")
        }
    }

    // Filters
    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        CategoryFilter(),
        LabelFilter(),
        StatusFilter(),
        RentalFilter(),
        Filter.Separator(),
        DailyFreeFilter(),
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
