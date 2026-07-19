package eu.kanade.tachiyomi.extension.zh.happymh

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

const val PREF_KEY_CUSTOM_UA = "pref_key_custom_ua_"

@Source
abstract class Happymh :
    KeiSource(),
    ConfigurableSource {

    private val preferences = getPreferences()
    private val decoder = Decoder()

    init {
        val oldUa = preferences.getString("userAgent", null)
        if (oldUa != null) {
            val editor = preferences.edit().remove("userAgent")
            if (oldUa.isNotBlank()) editor.putString(PREF_KEY_CUSTOM_UA, oldUa)
            editor.apply()
        }
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder {
        val userAgent = preferences.getString(PREF_KEY_CUSTOM_UA, "")!!
        return if (userAgent.isNotBlank()) {
            set("User-Agent", userAgent)
        } else {
            this
        }
    }

    // Popular + Latest
    override suspend fun getPopularManga(page: Int): MangasPage {
        // Requires login, otherwise result is the same as latest updates
        val headers = headers.newBuilder().set("Referer", "$baseUrl/latest").build()
        val response = client.get("$baseUrl/apis/c/index?pn=$page&series_status=-1&order=views", headers)
        return response.parseAs<PopularResponseDto>().toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val headers = headers.newBuilder().set("Referer", "$baseUrl/latest").build()
        val response = client.get("$baseUrl/apis/c/index?pn=$page&series_status=-1&order=last_date", headers)
        return response.parseAs<PopularResponseDto>().toMangasPage()
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val body = FormBody.Builder()
                .addEncoded("searchkey", query)
                .add("v", "v2.13")
                .build()
            val headers = headers.newBuilder().set("Referer", "$baseUrl/sssearch").build()
            val response = client.post("$baseUrl/v2.0/apis/manga/ssearch", headers, body)
            val mangasPage = response.parseAs<PopularResponseDto>().toMangasPage()
            return MangasPage(mangasPage.mangas, false)
        } else {
            val urlBuilder = "$baseUrl/apis/c/index".toHttpUrl().newBuilder()
            filters.filterIsInstance<UriPartFilter>().forEach {
                if (it.selected.isNotEmpty()) urlBuilder.addQueryParameter(it.key, it.selected)
            }
            val headers = headers.newBuilder().set("Referer", "$baseUrl/latest/${urlBuilder.build().query}").build()
            urlBuilder.addQueryParameter("pn", page.toString())
            val response = client.get(urlBuilder.build(), headers)
            return response.parseAs<PopularResponseDto>().toMangasPage()
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        GenreFilter(),
        AreaFilter(),
        AudienceFilter(),
        StatusFilter(),
    )

    // Details + Chapters
    private suspend fun getMangaDetails(manga: SManga): SManga {
        val response = client.get(getMangaUrl(manga))
        return parseMangaDetails(response)
    }

    private fun parseMangaDetails(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("div.mg-property > h2.mg-title")!!.text()
        thumbnail_url = document.selectFirst("div.mg-cover > mip-img")!!.attr("abs:src")
        author = document.selectFirst("div.mg-property > p.mg-sub-title:nth-of-type(2)")!!.text()
        artist = author
        genre = document.select("div.mg-property > p.mg-cate > a").eachText().joinToString(", ")
        description = document.selectFirst("div.manga-introduction > mip-showmore#showmore")!!.text()
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return doc.select("div.manga-recommended div.manga-cover").map { el ->
            SManga.create().apply {
                title = el.selectFirst("p.manga-title")!!.text()
                setUrlWithoutDomain(el.selectFirst("a[href*='/manga/']")!!.attr("href"))
                thumbnail_url = el.selectFirst("mip-img")!!.attr("abs:src")
            }
        }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = null

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) fetchAllChapters(manga) else chapters }

        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun fetchAllChapters(manga: SManga): List<SChapter> = coroutineScope {
        val comicId = "$baseUrl${manga.url}".toHttpUrl().pathSegments.last()

        val firstPage = fetchChapterByPage(comicId, 1)
        val chunkSize = firstPage.items.size
        val totalPages = if (chunkSize > 0) (firstPage.total + chunkSize - 1) / chunkSize else 1

        val deferred = (2..totalPages).map { page ->
            async { fetchChapterByPage(comicId, page) }
        }

        val allResponses = listOf(firstPage) + deferred.awaitAll()
        val itemsWithPage = allResponses.flatMap { data -> data.items.map { it to data.curr } }

        itemsWithPage.map { (chapter, pageNum) ->
            SChapter.create().apply {
                name = chapter.chapterName
                // create a dummy chapter url : /comic_id/dummy_mark/chapter_id#expect_page
                url = "/$comicId/$DUMMY_CHAPTER_MARK/${chapter.id}#$pageNum"
            }
        }.reversed()
    }

    private suspend fun fetchChapterByPage(comicId: String, page: Int): ChapterByPageResponseData {
        val requestId = System.currentTimeMillis().toString()
        val url = "$baseUrl/v2.0/apis/manga/chapterByPage".toHttpUrl().newBuilder()
            .addQueryParameter("code", comicId)
            .addQueryParameter("lang", "cn")
            .addQueryParameter("order", "asc")
            .addQueryParameter("page", "$page")
            .addQueryParameter("_t", requestId)
            .build()
        val headers = ajaxHeadersBuilder(requestId).build()
        val response = client.get(url, headers)
        return response.parseAs<ChapterByPageResponse>().data
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = "$baseUrl${chapter.url}".toHttpUrl()
        val comicId = url.pathSegments[0]
        val chapterId = url.pathSegments[2]
        return "$baseUrl/mangaread/$comicId/$chapterId"
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (!chapter.url.contains(DUMMY_CHAPTER_MARK)) {
            // Old format is detected
            throw Exception("请刷新章节列表")
        }
        val requestId = System.currentTimeMillis().toString()
        val chapterUrl = "$baseUrl${chapter.url}".toHttpUrl()
        val comicId = chapterUrl.pathSegments[0]
        val chapterId = chapterUrl.pathSegments[2]

        val url = "$baseUrl/v2.0/apis/manga/reading".toHttpUrl().newBuilder()
            .addQueryParameter("code", comicId)
            .addQueryParameter("cid", chapterId)
            .addQueryParameter("v", "v4.300102")
            .addQueryParameter("_t", requestId)
            .build()
        val headers = ajaxHeadersBuilder(requestId, accept = "application/json")
            .set("Referer", "$baseUrl/mangaread/$comicId/$chapterId")
            .build()

        // Replicate the _ga_HVJMXGJXFJ cookie generation from the website (VQ/VB in main.*.js).
        // gaTimestamp = 10-digit seconds timestamp + 3-digit checksum from a lookup table.
        val gaTimestamp = generateGaTimestamp()
        val cookie = Cookie.Builder()
            .name("_ga_HVJMXGJXFJ")
            .value("GS2.1.s${gaTimestamp}\$o9\$g1\$t${gaTimestamp + 99999}\$j43\$l0\$h0")
            .domain(baseUrl.toHttpUrl().host)
            .path("/")
            .expiresAt(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
            .build()
        client.cookieJar.saveFromResponse(url, listOf(cookie))

        val response = client.get(url, headers)
        val dto = response.parseAs<PageListResponseDto>()

        val pages = if (dto.data.isEncode) {
            decoder.decodeScans(dto.data.scans)
        } else {
            dto.data.scans
        }

        return pages.parseAs<List<PageDto>>()
            // If n == 1, the image is from next chapter
            .filter { it.n == 0 }
            .mapIndexed { index, page ->
                Page(index, imageUrl = page.url.substringBefore("?q="))
            }
    }

    // Preferences
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        EditTextPreference(context).apply {
            key = PREF_KEY_CUSTOM_UA
            title = "User Agent"
            summary = "留空则使用应用设置中的默认 User Agent，重启生效"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    Headers.headersOf("User-Agent", newValue as String)
                    true
                } catch (e: Throwable) {
                    Toast.makeText(context, "User Agent 无效：${e.message}", Toast.LENGTH_LONG)
                        .show()
                    false
                }
            }
        }.let(screen::addPreference)
    }

    // Utils
    private fun ajaxHeadersBuilder(
        requestId: String,
        accept: String = "application/json, text/plain, */*",
    ): Headers.Builder = headers.newBuilder()
        .set("Accept", accept)
        .add("X-Requested-With", "XMLHttpRequest")
        .add("X-Requested-Id", requestId)

    /**
     * Corresponds to the VB() function in the website's main.*.js.
     * Generates a 13-digit pseudo-millisecond timestamp:
     * 1. Take the Unix timestamp in seconds (10 digits).
     * 2. Use the last 3 digits as indices into a hardcoded lookup table.
     * 3. Sum the 3 looked-up values, take the first 3 chars as a checksum.
     * 4. Concatenate: seconds(10) + checksum(3) = 13-digit timestamp.
     */
    private fun generateGaTimestamp(): Long {
        // Digit-to-value lookup table from the obfuscated JS source
        val table = intArrayOf(335, 984, 248, 485, 524, 559, 486, 165, 114, 103)
        val seconds = (System.currentTimeMillis() / 1000).toString()
        val len = seconds.length
        val sum = table[seconds[len - 3] - '0'] +
            table[seconds[len - 2] - '0'] +
            table[seconds[len - 1] - '0']
        return (seconds + sum.toString().take(3)).toLong()
    }

    companion object {
        private const val DUMMY_CHAPTER_MARK = "dummy-mark"
    }
}
