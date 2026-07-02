package eu.kanade.tachiyomi.extension.ja.yanmaga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.lib.speedbinb.SpeedBinbInterceptor
import keiyoushi.lib.speedbinb.SpeedBinbReader
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Yanmaga : HttpSource() {

    private val isGravure get() = name.contains("グラビア")
    private val searchCategoryClass get() = if (isGravure) "search-item-category--gravures" else "search-item-category--comics"
    private val highQualityImages get() = isGravure

    override val supportsLatest get() = !isGravure

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ROOT)

    override val client = network.client.newBuilder()
        .addInterceptor(SpeedBinbInterceptor(jsonInstance))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Kept to carry state to offset requests specifically for this source's unique pagination
    private var latestUpdatesCsrfToken: String? = null
    private var latestUpdatesMoreUrl: String? = null
    private var latestUpdatesCount: Int = 0

    // ============================== Popular ==============================

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = if (isGravure) {
        super.fetchPopularManga(page)
    } else {
        client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                val document = response.asJsoup()
                val directory = document.select("a.ga-comics-book-item")
                val endRange = minOf(page * 24, directory.size)
                val manga = directory.subList((page - 1) * 24, endRange).map { popularMangaFromElement(it) }
                val hasNextPage = endRange < directory.lastIndex

                MangasPage(manga, hasNextPage)
            }
    }

    override fun popularMangaRequest(page: Int): Request = if (isGravure) {
        GET("$baseUrl/gravures/series?page=$page", headers)
    } else {
        GET("$baseUrl/comics", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (isGravure) {
            val document = response.asJsoup()
            val mangas = document.select("a.banner-link").map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.attr("href"))
                    title = element.selectFirst(".text-wrapper h2")!!.text()
                    thumbnail_url = element.selectFirst(".img-bg-wrapper")?.absUrl("data-bg")
                }
            }
            val hasNextPage = document.selectFirst("ul.pagination > li.page-item > a.page-next") != null
            return MangasPage(mangas, hasNextPage)
        } else {
            throw UnsupportedOperationException()
        }
    }

    private fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst(".mod-book-title")!!.text()
        thumbnail_url = element.selectFirst(".mod-book-image img")?.absUrl("data-src")
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        if (isGravure) throw UnsupportedOperationException()

        val pageUrl = "$baseUrl/comics/series/newer"
        if (page == 1) {
            return GET(pageUrl, headers)
        }

        val offset = (page - 1) * LATEST_UPDATES_PER_PAGE
        val headers = headers.newBuilder()
            .set("Referer", pageUrl)
            .set("X-CSRF-Token", latestUpdatesCsrfToken ?: "")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        return GET("$latestUpdatesMoreUrl?offset=$offset", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        if (isGravure) throw UnsupportedOperationException()

        val pageUrl = "$baseUrl/comics/series/newer"
        val url = response.request.url

        return if (url.pathSegments.last() == "newer") {
            val document = response.asJsoup()

            latestUpdatesCsrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content")
            document.selectFirst(".newer-older-episode-more-button[data-count][data-path]")?.let {
                latestUpdatesMoreUrl = it.attr("abs:data-path")
                latestUpdatesCount = it.attr("data-count").toInt()
            }

            val manga = document.select("#comic-episodes-newer > div")
                .map { latestUpdatesFromElement(it) }
            val hasNextPage = latestUpdatesCount > LATEST_UPDATES_PER_PAGE

            MangasPage(manga, hasNextPage)
        } else {
            val offset = url.queryParameter("offset")!!.toInt()
            // Wrapped in .use { } block to prevent OkHttp resource leak
            val manga = parseInsertAdjacentHtmlScript(response.use { it.body.string() })
                .map { latestUpdatesFromElement(Jsoup.parseBodyFragment(it, pageUrl)) }
            val hasNextPage = offset + LATEST_UPDATES_PER_PAGE < latestUpdatesCount

            MangasPage(manga, hasNextPage)
        }
    }

    private fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.selectFirst(".text-wrapper h2")!!.text()
        thumbnail_url = element.selectFirst(".img-bg-wrapper")?.absUrl("data-bg")
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("q", query)
            addQueryParameter("kind", "human")

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }

            addQueryParameter("search-submit", "")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.search-list > li.search-item:has(.$searchCategoryClass)").map {
            searchMangaFromElement(it)
        }
        val hasNextPage = document.selectFirst("ul.pagination > li.page-item > a.page-next") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.selectFirst(".search-item-title")!!.text()
        thumbnail_url = element.selectFirst(".search-item-thumbnail-image img")?.absUrl("src")

        if (isGravure) {
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // ============================== Details ==============================

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = if (isGravure && !manga.url.contains("/series/")) {
        Observable.just(manga)
    } else {
        super.fetchMangaDetails(manga)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            if (isGravure) {
                title = document.selectFirst(".detail-header-title")!!.text()
                genre = document.select(".ga-tag").joinToString { it.text() }
                thumbnail_url = document.selectFirst(".detail-header-image img")?.absUrl("src")
            } else {
                title = document.selectFirst(".detailv2-outline-title")!!.text()
                author = document.select(".detailv2-outline-author-item a").joinToString { it.text() }
                description = document.selectFirst(".detailv2-description")?.text()
                genre = document.select(".detailv2-tag .ga-tag").joinToString { it.text() }
                thumbnail_url = document.selectFirst(".detailv2-thumbnail-image img")?.absUrl("src")
                status = if (document.selectFirst(".detailv2-link-note") != null) {
                    SManga.ONGOING
                } else {
                    SManga.COMPLETED
                }
            }
        }
    }

    // ============================= Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = if (isGravure && !manga.url.contains("/series/")) {
        Observable.just(
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "作品"
                },
            ),
        )
    } else {
        super.fetchChapterList(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        if (document.selectFirst(".js-episode") == null) {
            return document.select("ul.mod-episode-list > li.mod-episode-item:has(.mod-episode-title)")
                .map { chapterFromElement(it) }
                .filter { it.url.isNotEmpty() }
        }

        val chapterUrl = response.request.url.toString()
        val firstChapterList = document
            .select("ul.mod-episode-list:first-of-type > li.mod-episode-item:has(.mod-episode-title)")
            .map { chapterFromElement(it) }
        val lastChapterList = document
            .select("ul.mod-episode-list:last-of-type > li.mod-episode-item:has(.mod-episode-title)")
            .map { chapterFromElement(it) }

        val totalChapterCount = document
            .selectFirst("#contents")
            ?.attr("data-count")
            ?.toInt()
            ?: return firstChapterList + lastChapterList

        val chapterMoreButton = document.selectFirst(".mod-episode-more-button[data-offset][data-path]")
            ?: return firstChapterList + lastChapterList

        val chapterOffset = chapterMoreButton.attr("data-offset").toInt()
        val chapterAjaxUrl = chapterMoreButton.attr("abs:data-path").toHttpUrl()
        val chaptersPerPage = document
            .selectFirst("script:containsData(gon.episode_more)")
            ?.data()
            ?.substringAfter("gon.episode_more=")
            ?.substringBefore(";")
            ?.toInt()
            ?: 150

        val headers = headers.newBuilder()
            .set("Referer", chapterUrl)
            .set("X-CSRF-Token", document.selectFirst("meta[name=csrf-token]")!!.attr("content"))
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        return buildList(totalChapterCount) {
            addAll(firstChapterList)

            for (i in chapterOffset until totalChapterCount - lastChapterList.size step chaptersPerPage) {
                val limit = totalChapterCount - lastChapterList.size - i
                val url = chapterAjaxUrl.newBuilder().apply {
                    addQueryParameter("offset", i.toString())

                    if (limit < 150) {
                        addQueryParameter("limit", limit.toString())
                    }

                    addQueryParameter("cb", System.currentTimeMillis().toString())
                }.build()

                val script = client.newCall(GET(url, headers)).execute().use { it.body.string() }

                parseInsertAdjacentHtmlScript(script)
                    .map { chapterFromElement(Jsoup.parseBodyFragment(it, chapterUrl)) }
                    .let { addAll(it) }
            }

            addAll(lastChapterList)
        }.filter { it.url.isNotEmpty() }
    }

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        url = ""
        element.selectFirst("a.mod-episode-link")?.attr("href")?.let {
            setUrlWithoutDomain(it)
        }
        name = element.selectFirst(".mod-episode-title")!!.text()
        date_upload = dateFormat.tryParse(element.selectFirst(".mod-episode-date")?.text())
    }

    // =============================== Pages ===============================

    private val reader by lazy { SpeedBinbReader(client, headers, jsonInstance, highQualityImages) }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        if (document.selectFirst(".ga-rental-modal-sign-up") != null) {
            throw Exception("このストーリーを読むには WebView でログイン")
        }

        if (document.selectFirst(".ga-modal-open") != null) {
            throw Exception("WebView でポイントを使用してこのストーリーをレンタル")
        }

        return reader.pageListParse(document)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val LATEST_UPDATES_PER_PAGE = 12
    }
}
