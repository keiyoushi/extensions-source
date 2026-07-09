package eu.kanade.tachiyomi.extension.ko.wolfdotcom

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import rx.Observable
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Wolf : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .addNetworkInterceptor(::refererInterceptor)
        .build()

    private val isWebtoon get() = name.endsWith("웹툰")
    private val isComic get() = name.endsWith("만화책")
    private val isPhoto get() = name.endsWith("포토툰")

    private val browsePath get() = when {
        isComic -> "cm"
        isPhoto -> "pt"
        else -> "ing" // Webtoon
    }

    private val entryPath get() = when {
        isComic -> "cl"
        else -> "list"
    }

    private val readerPath get() = when {
        isComic -> "cv"
        else -> "view"
    }

    // ============================== Popular ==============================

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = fetchSearchManga(page, "", POPULAR)

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    // ============================== Latest ===============================

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = fetchSearchManga(page, "", LATEST)

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    // ============================== Search ===============================

    private lateinit var browseCache: List<List<BrowseItem>>

    class BrowseItem(
        val id: Int,
        val title: String,
        val cover: String?,
    )

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isNotBlank()) {
            return querySearch(query)
        }

        return if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map {
                    parseBrowsePage(it)
                    paginatedBrowsePage(0)
                }
        } else {
            Observable.just(
                paginatedBrowsePage(page - 1),
            )
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/$browsePath".toHttpUrl().newBuilder().apply {
            filters.filterIsInstance<UrlPartFilter>().forEach { filter ->
                filter.addToUrl(this)
            }
        }.build()

        return GET(url, headers)
    }

    private fun parseBrowsePage(response: Response) {
        val document = response.asJsoup()

        parseSearchFilters(document)

        browseCache = document.select(".webtoon-list > ul > li > a").map {
            val id = it.absUrl("href").toHttpUrl()
                .queryParameter("toon")!!.toInt()

            BrowseItem(
                id = id,
                title = it.selectFirst(".txt > .subject")!!.ownText(),
                cover = it.selectFirst(".img > img")?.attr("data-original"),
            )
        }.chunked(20)
    }

    private fun paginatedBrowsePage(index: Int): MangasPage = MangasPage(
        browseCache[index].map {
            SManga.create().apply {
                url = it.id.toString()
                title = it.title
                thumbnail_url = it.cover
            }
        },
        browseCache.lastIndex > index,
    )

    private val specialChars = Regex("""[^\p{InHangul_Syllables}0-9a-z ]""", RegexOption.IGNORE_CASE)
    private val styleImage = Regex("""background-image:url\(([^)]+)\)""")

    private fun querySearch(query: String): Observable<MangasPage> {
        if (query.length < 2) {
            throw Exception("두 글자 이상 입력 해주세요.")
        }
        val stdQuery = query.replace(specialChars, "")
        val searchUrl = "$baseUrl/search.html?q=${URLEncoder.encode(stdQuery, "EUC-KR")}"

        return client.newCall(GET(searchUrl, headers))
            .asObservableSuccess()
            .map { response ->
                val document = Jsoup.parseBodyFragment(response.body.string(), searchUrl)
                val entries = document.select("article.searchItem")
                    .filter { el ->
                        el.selectFirst("a.searchLink")!!.attr("href").contains(entryPath)
                    }
                    .map { el ->
                        val mangaUrl = el.selectFirst("a.searchLink")!!.absUrl("href")
                            .toHttpUrl()
                        SManga.create().apply {
                            url = mangaUrl.queryParameter("toon")!!
                            title = el.selectFirst(".searchDetailTitle")!!.text()
                            thumbnail_url = el.selectFirst(".searchPng")
                                ?.attr("style")
                                ?.let {
                                    styleImage.find(it)?.groupValues?.get(1)
                                }
                        }
                    }

                MangasPage(entries, false)
            }
    }

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(entryPath)
        .addQueryParameter("toon", manga.url)
        .toString()

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".text-box h1")!!.text()
            thumbnail_url = document.selectFirst(".img-box img")?.absUrl("src")
            description = document.selectFirst(".text-box .txt")?.text()
            genre = document.selectFirst(".text-box .sub:has(> strong:contains(장르))")?.ownText()?.replace("/", ", ")
            author = document.selectFirst(".text-box .sub:has(> strong:contains(작가))")?.ownText()?.replace("/", ", ")
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    @Serializable
    class ChapterUrl(
        val toon: String,
        val num: String,
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".webtoon-bbs-list a.view_open").map { el ->
            val chapUrl = el.absUrl("href").toHttpUrl()
            SChapter.create().apply {
                url = ChapterUrl(
                    chapUrl.queryParameter("toon")!!,
                    chapUrl.queryParameter("num")!!,
                ).toJsonString()
                name = el.selectFirst(".subject")!!.ownText()
                date_upload = dateFormat.tryParse(el.selectFirst(".date")?.text())
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapUrl = chapter.url.parseAs<ChapterUrl>()

        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(readerPath)
            .addQueryParameter("toon", chapUrl.toon)
            .addQueryParameter("num", chapUrl.num)
            .toString()
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".image-view img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("data-original"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    private var searchFilters: List<FilterData> = emptyList()
    private var filterParseError = false

    override fun getFilterList(): FilterList {
        if (isPhoto) return FilterList()

        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter(),
        )

        if (searchFilters.isNotEmpty()) {
            filters.add(
                SearchFilter(searchFilters),
            )
        } else if (filterParseError) {
            filters.add(
                Filter.Header("unable to parse filters"),
            )
        } else {
            filters.add(
                Filter.Header("press 'reset' to attempt to load more filters"),
            )
        }

        return FilterList(filters)
    }

    private fun parseSearchFilters(document: Document) {
        if (isPhoto || searchFilters.isNotEmpty() || filterParseError) return

        try {
            val displayName = document.select(".sub-tab > a").eachText()
            assert(displayName.size == 3)
            searchFilters =
                document.select(".tab-day > a, .tab-genre1 > a, .tab-genre2 > a, .tab-alphabet > a")
                    .map {
                        val url = it.absUrl("href").toHttpUrl()
                        val type = url.queryParameter("type1")!!
                        FilterData(
                            type = type,
                            typeDisplayName = when (type) {
                                "day", "complete" -> displayName[0]
                                "genre" -> displayName[1]
                                "alphabet" -> displayName[2]
                                else -> null
                            },
                            value = url.queryParameter("type2"),
                            valueDisplayName = it.ownText(),
                        )
                    }
        } catch (e: Throwable) {
            Log.e(name, "error parsing filters", e)
            filterParseError = true
        }
    }

    // ============================= Utilities =============================

    private fun refererInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Referer", "$baseUrl/")
            .build()

        return chain.proceed(request)
    }

    companion object {
        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        }
    }
}
