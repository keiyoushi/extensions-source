package eu.kanade.tachiyomi.extension.ja.mechacomic

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
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class MechaComic :
    KeiSource(),
    ConfigurableSource {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "$baseUrl/api/v1"
    private val recommendApiUrl get() = "https://api.one.$domain/v1/recommendation"
    private val cdnUrl get() = "https://c.$domain"
    private val preferences by getPreferencesLazy()
    private val desktopHeaders get() = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36")
        .build()

    private val noRedirectClient get() = client.newBuilder()
        .followRedirects(false)
        .build()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie { listOf("_taste" to "all", "_confirmed_adult" to "1") }
        addInterceptor(ImageInterceptor())
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/sales_rankings/current".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        val result = client.get(url).parseAs<RankingResponse>()
        val mangas = result.rankingBooks.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$recommendApiUrl/recent".toHttpUrl().newBuilder()
            .addQueryParameter("gender", "all")
            .addQueryParameter("is_adult", "true")
            .addQueryParameter("service_name", "web")
            .addQueryParameter("arrival_type", "new_title")
            .addQueryParameter("content_format", "all")
            .addQueryParameter("sort", "newest")
            .addQueryParameter("page", page.toString())
            .build()

        val result = client.get(url).parseAs<RecentResponse>()
        val mangas = result.books.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, result.hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstance<GenreFilter>().value
        val sort = filters.firstInstance<SortFilter>().value
        val completed = filters.firstInstance<CompletedFilter>().state

        val url = "$baseUrl/books".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotBlank()) addQueryParameter("text", query)
                if (genre.isNotEmpty()) addQueryParameter("genre", genre)
                if (sort.isNotEmpty()) addQueryParameter("sort", sort)
                if (completed) addQueryParameter("filter[]", "completed")
            }
            .build()

        val document = client.get(url, desktopHeaders).asJsoup()
        val mangas = document.select("li.p-bookList_item").map {
            val link = it.selectFirst("dt.p-book_title a")!!
            SManga.create().apply {
                this.url = link.absUrl("href").toHttpUrl().pathSegments.last()
                title = link.text()
                thumbnail_url = it.selectFirst("div.p-book_jacket img[class^=jacket_image]")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a.next_page") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/books/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), desktopHeaders).asJsoup()
        val details = SManga.create().apply {
            title = document.selectFirst(".p-bookInfo_title h1")!!.text()
            author = document.select("#js-anchor-defList dt:contains(作家) + dd .p-sepList_item").joinToString { it.text() }
            genre = document.select("#js-anchor-defList dt:contains(ジャンル) + dd a, #js-anchor-defList dt:contains(タグ) + dd a").joinToString { it.text() }
            description = document.selectFirst(".p-bookInfo_summary p")?.textOrNull()
            status = if (document.selectFirst(".p-bookInfo .c-tag-completed") != null) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = document.selectFirst(".p-bookInfo_jacket img")?.absUrl("src")
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = mutableListOf<SChapter>()

        val volumesUrl = document.selectFirst("a.c-nav_link[href$=/volumes]")?.absUrl("href")
        if (volumesUrl != null) {
            var page = 1
            var hasNextPage = true
            while (hasNextPage) {
                val volumesDocument = getBookPage(volumesUrl, page)
                chapterList += volumesDocument.parseVolumes(manga.url, hideLocked)
                hasNextPage = volumesDocument.selectFirst("a.next_page") != null
                page++
            }
        }

        var page = 1
        var pageDocument = document
        while (true) {
            chapterList += pageDocument.parseChapters(hideLocked)
            chapterList += pageDocument.parseVolumes(manga.url, hideLocked)
            if (pageDocument.selectFirst("a.next_page") == null) break
            pageDocument = getBookPage(getMangaUrl(manga), ++page)
        }

        return SMangaUpdate(
            details,
            chapterList.reversed(),
        )
    }

    private suspend fun getBookPage(url: String, page: Int): Document {
        val pageUrl = url.toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(pageUrl, desktopHeaders).asJsoup()
    }

    private fun Document.parseChapters(hideLocked: Boolean): List<SChapter> = select("ol.p-chapterList li.p-chapterList_item:has(dl.p-chapterList_txtArea)").mapNotNull {
        val downloadPath = it.selectFirst("a.p-btn-chapter[href*=download]")?.absUrl("href")?.toHttpUrl()?.encodedPath
        val isReadable = downloadPath != null || it.selectFirst("a.p-btn-chapter[class*=c-btn-read]") != null
        if (hideLocked && !isReadable) return@mapNotNull null

        val number = it.selectFirst("dt.p-chapterList_no")?.ownTextOrNull()
        SChapter.create().apply {
            val lock = if (isReadable) "" else "🔒 "
            url = it.selectFirst("input[name=\"chapter_ids[]\"]")!!.attr("value")
            name = lock + it.selectFirst("dd.p-chapterList_name")!!.text()
            chapter_number = number?.removeSuffix("話")?.toFloatOrNull() ?: -1f
            if (downloadPath != null) {
                memo = buildJsonObject {
                    put("download", downloadPath)
                }
            }
        }
    }

    private fun Document.parseVolumes(bookId: String, hideLocked: Boolean): List<SChapter> = select("ol.p-volumeList li.p-volumeList_item:has(dl.p-volumeInfo_body)").mapNotNull {
        val link = it.selectFirst("dt.p-volumeList_no a")!!
        val downloadPaths = it.select("a.p-btn-volume[href*=download]").map { btn -> btn.absUrl("href").toHttpUrl().encodedPath }
        val fullPath = downloadPaths.firstOrNull { path -> !path.endsWith("/sample_download") }
        if (hideLocked && fullPath == null) return@mapNotNull null

        val downloadPath = fullPath ?: downloadPaths.firstOrNull()
        SChapter.create().apply {
            val lock = when {
                fullPath != null -> ""
                downloadPath != null -> "🔒 (Preview) "
                else -> "🔒 "
            }
            url = link.absUrl("href").toHttpUrl().pathSegments.last()
            name = lock + link.text()
            memo = buildJsonObject {
                put("bookId", bookId)
                if (downloadPath != null) put("download", downloadPath)
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        chapter.memo.getStringOrNull("download")?.let { return baseUrl + it }
        val bookId = chapter.memo.getStringOrNull("bookId")
        return if (bookId != null) {
            "$baseUrl/books/$bookId/volume/${chapter.url}/download"
        } else {
            "$baseUrl/chapters/${chapter.url}/download"
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = coroutineScope {
        val viewerUrl = noRedirectClient.get(getChapterUrl(chapter), ensureSuccess = false)
            .use { it.header("Location") }
            ?.toHttpUrlOrNull()
        val contentsUrl = viewerUrl?.let {
            it.queryParameter("contents_vertical")
                ?: it.queryParameter("contents")
                ?: it.queryParameter("contents_page")
        } ?: throw Exception("Log in via WebView and purchase this product to read.")

        val directory = viewerUrl.queryParameter("directory")!!
        val manifestPath = viewerUrl.queryParameter("manifest_url")!!
        val contentsRequestUrl = contentsUrl.toHttpUrl().newBuilder()
            .addQueryParameter("ver", viewerUrl.queryParameter("ver"))
            .build()

        val contentData = async { client.get(contentsRequestUrl).parseAs<ContentData>() }
        val cryptoKey = async { client.get(baseUrl + manifestPath).parseAs<CryptoKey>().cryptokey }

        contentData.await().imagePaths().mapIndexed { i, path ->
            Page(i, imageUrl = "$directory$path#key=${cryptoKey.await()}")
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Novels are not supported!"),
        GenreFilter(),
        SortFilter(),
        CompletedFilter(),
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
