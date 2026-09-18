package eu.kanade.tachiyomi.extension.en.bbato

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Bbato : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        interceptors().removeAll { it.javaClass.simpleName == "CloudflareInterceptor" }
        rateLimit(2)
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        removeAll("Origin")
        set("Referer", "$baseUrl/")
    }

    private suspend fun getDocument(url: String): Document {
        val html = runWebView<String>(timeout = 30.seconds) {
            loadWithOverviewMode = true
            useWideViewPort = true

            jsBridge("bridge") { rawHtml ->
                resolve(rawHtml)
            }
            poll(500.milliseconds) {
                evaluateJs(
                    """
                    (function() {
                        var content = document.querySelector(".unit, h1[itemprop=name], .pages, #most-viewed, footer");
                        if (content && document.body && document.body.innerHTML.length > 200) {
                            window.bridge.post(document.documentElement.outerHTML);
                        }
                    })()
                    """.trimIndent(),
                )
            }
            loadUrl(url)
        }
        return Jsoup.parse(html, url)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = getLatestUpdates(page)

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val path = if (page == 1) "/updated" else "/updated/page/$page"
        val document = getDocument("$baseUrl$path")
        val mangas = document.select(".original.card-lg .unit").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.poster")!!.attr("abs:href"))
                title = element.selectFirst(".info > a")?.text() ?: throw Exception("Missing title")
                thumbnail_url = element.selectFirst("a.poster img")?.getImageUrl()
            }
        }

        val hasNext = document.selectFirst(".pagination a[rel=next]") != null
        return MangasPage(mangas, hasNext)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/filter".toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", query)

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }

            filters.firstInstanceOrNull<TypeFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("type[]", it.value) }
            filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("genre[]", it.value) }
            filters.firstInstanceOrNull<StatusFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("status[]", it.value) }
            filters.firstInstanceOrNull<YearFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("year[]", it.value) }

            filters.firstInstanceOrNull<MinChapterFilter>()?.selectedValue?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("minchap", it)
            }

            filters.firstInstanceOrNull<SortFilter>()?.selectedValue?.let {
                addQueryParameter("sort", it)
            }
        }.build()

        val document = getDocument(url.toString())
        val mangas = document.select(".original.card-lg .unit").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.poster")!!.attr("abs:href"))
                title = element.selectFirst(".info > a")?.text() ?: throw Exception("Missing title")
                thumbnail_url = element.selectFirst("a.poster img")?.getImageUrl()
            }
        }

        val hasNext = document.selectFirst(".pagination a[rel=next]") != null
        return MangasPage(mangas, hasNext)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val mangaUrl = when {
            url.encodedPath.startsWith("/manga/") -> url.encodedPath
            url.encodedPath.startsWith("/read/") -> {
                val slug = url.encodedPath.removePrefix("/read/").substringBefore("/")
                "/manga/$slug"
            }
            else -> return null
        }
        val document = getDocument(baseUrl + mangaUrl)
        return SManga.create().apply {
            setUrlWithoutDomain(mangaUrl)
            title = document.selectFirst("h1[itemprop=name]")?.text() ?: return null
            author = document.select(".meta div:has(span:contains(Author)) a").joinToString { it.text() }
            description = document.selectFirst(".description")?.text()
            genre = document.select(".meta div:has(span:contains(Genres)) a").joinToString { it.text() }
            status = document.selectFirst(".info > p")?.text().toStatus()
            thumbnail_url = document.selectFirst(".poster img")?.getImageUrl()
        }
    }

    // ============================== Details & Chapters ==============================

    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private suspend fun getChapterJson(mangaUrl: String, slug: String): String = runWebView<String>(timeout = 30.seconds) {
        loadWithOverviewMode = true
        useWideViewPort = true

        jsBridge("bridge") { json ->
            resolve(json)
        }
        poll(500.milliseconds) {
            evaluateJs(
                """
                    (function() {
                        if (!window._fetchingChapters && document.querySelector("h1[itemprop=name], footer")) {
                            window._fetchingChapters = true;
                            fetch('$baseUrl/get-chapter-list?slug=$slug', {
                                headers: {
                                    'Accept': 'application/json, text/javascript, */*; q=0.01',
                                    'X-Requested-With': 'XMLHttpRequest'
                                }
                            })
                            .then(function(r) { return r.text(); })
                            .then(function(t) { window.bridge.post(t); })
                            .catch(function(e) { window.bridge.post('{"data":[]}'); });
                        }
                    })()
                """.trimIndent(),
            )
        }
        loadUrl(baseUrl + mangaUrl)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) {
            val document = getDocument(baseUrl + manga.url)
            SManga.create().apply {
                title = document.selectFirst("h1[itemprop=name]")?.text() ?: throw Exception("Missing title")
                author = document.select(".meta div:has(span:contains(Author)) a").joinToString { it.text() }
                description = document.selectFirst(".description")?.text()
                genre = document.select(".meta div:has(span:contains(Genres)) a").joinToString { it.text() }
                status = document.selectFirst(".info > p")?.text().toStatus()
                thumbnail_url = document.selectFirst(".poster img")?.getImageUrl()
            }
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters) {
            val slug = manga.url.substringAfterLast("/")
            val json = getChapterJson(manga.url, slug)
            val responseDto = json.parseAs<ChapterListResponse>()
            responseDto.toSChapterList(slug, dateTimeFormat)
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun String?.toStatus(): Int = when (this?.lowercase(Locale.ENGLISH)) {
        "ongoing", "releasing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "on hiatus" -> SManga.ON_HIATUS
        "discontinued", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = getDocument(baseUrl + chapter.url)

        return document.select(".pages .page:not(.notice-page) img").mapIndexedNotNull { index, img ->
            img.getImageUrl()?.let { Page(index, imageUrl = it) }
        }
    }

    override fun imageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(
            headers.newBuilder()
                .removeAll("Origin")
                .set("Referer", "$baseUrl/")
                .build(),
        )
        .build()

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // ============================= Utilities =============================

    private fun Element.getImageUrl(): String? = attr("abs:data-src").ifEmpty { attr("abs:src") }.takeIf { it.isNotEmpty() }
}
