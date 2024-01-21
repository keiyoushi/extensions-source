package eu.kanade.tachiyomi.extension.all.hentaicosplay

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import rx.Single
import rx.schedulers.Schedulers
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiCosplay : HttpSource() {

    override val name = "Hentai Cosplay"

    override val baseUrl = "https://hentai-cosplays.com"

    override val lang = "all"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val dateCache = mutableMapOf<String, String>()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        fetchFilters()
        return super.fetchPopularManga(page)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/ranking/page/$page/", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        return if (document.selectFirst("div.image-list-item") == null) {
            parseMobileListing(document)
        } else {
            parseDesktopListing(document)
        }
    }

    private fun parseMobileListing(document: Document): MangasPage {
        val entries = document.select("#entry_list > li > a[href*=/image/]")
            .map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    thumbnail_url = element.selectFirst("img")
                        ?.absUrl("data-original")
                        ?.replace("http://", "https://")
                    title = element.selectFirst("span:not(.posted)")!!.text()
                    element.selectFirst("span.posted")
                        ?.text()?.also { dateCache[url] = it }
                    update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                    status = SManga.COMPLETED
                    initialized = true
                }
            }
        val hasNextPage = document.selectFirst("a.paginator_page[rel=next]") != null

        return MangasPage(entries, hasNextPage)
    }

    private fun parseDesktopListing(document: Document): MangasPage {
        val entries = document.select("div.image-list-item:has(a[href*=/image/])")
            .map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                    thumbnail_url = element.selectFirst("img")
                        ?.absUrl("src")
                        ?.replace("http://", "https://")
                    title = element.select(".image-list-item-title").text()
                    element.selectFirst(".image-list-item-regist-date")
                        ?.text()?.also { dateCache[url] = it }
                    update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                    status = SManga.COMPLETED
                    initialized = true
                }
            }
        val hasNextPage = document.selectFirst("div.wp-pagenavi > a[rel=next]") != null

        return MangasPage(entries, hasNextPage)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        fetchFilters()
        return super.fetchLatestUpdates(page)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/recently/page/$page/", headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        fetchFilters()
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val keyword = query.trim().replace(" ", "+")
            return GET("$baseUrl/search/keyword/$keyword/page/$page/", headers)
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is TagFilter -> {
                        if (filter.selected.isNotEmpty()) {
                            return GET("$baseUrl${filter.selected}page/$page/", headers)
                        }
                    }
                    is KeywordFilter -> {
                        if (filter.selected.isNotEmpty()) {
                            return GET("$baseUrl${filter.selected}page/$page/", headers)
                        }
                    }
                    else -> {}
                }
            }

            return GET("$baseUrl/search/page/$page/", headers)
        }
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    private var keyWordCache: List<Pair<String, String>> = emptyList()
    private var tagCache: List<Pair<String, String>> = emptyList()

    private fun fetchFilters() {
        if (keyWordCache.isEmpty()) fetchKeywords()
        if (tagCache.isEmpty()) fetchTags()
    }

    private fun fetchTags() {
        Single.fromCallable {
            client.newCall(GET("$baseUrl/ranking-tag/", headers))
                .execute().asJsoup()
                .run {
                    tagCache = buildList {
                        add(Pair("", ""))
                        select("#tags a").map {
                            Pair(
                                it.text()
                                    .replace(tagNumRegex, "")
                                    .trim(),
                                it.attr("href"),
                            ).let(::add)
                        }
                    }
                }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe(
                {},
                {
                    Log.e(name, it.stackTraceToString())
                },
            )
    }

    private fun fetchKeywords() {
        Single.fromCallable {
            client.newCall(GET("$baseUrl/ranking-keyword/", headers))
                .execute().asJsoup()
                .run {
                    keyWordCache = buildList {
                        add(Pair("", ""))
                        select("#tags a").map {
                            add(Pair(it.text(), it.attr("href")))
                        }
                    }
                }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe(
                {},
                {
                    Log.e(name, it.stackTraceToString())
                },
            )
    }

    private abstract class SelectFilter(
        name: String,
        private val options: List<Pair<String, String>>,
    ) : Filter.Select<String>(
        name,
        options.map { it.first }.toTypedArray(),
    ) {
        val selected get() = options[state].second
    }

    private class TagFilter(name: String, options: List<Pair<String, String>>) : SelectFilter(name, options)
    private class KeywordFilter(name: String, options: List<Pair<String, String>>) : SelectFilter(name, options)

    override fun getFilterList(): FilterList {
        return if (keyWordCache.isEmpty()) {
            FilterList(Filter.Header("Press reset to attempt to load filters"))
        } else {
            FilterList(
                Filter.Header("Ignored with text search"),
                Filter.Header("Tag Filtered is preferred over Keyword filter"),
                Filter.Separator(),
                TagFilter("Ranked Tags", tagCache),
                KeywordFilter("Ranked Keywords", keyWordCache),
            )
        }
    }

    override fun fetchMangaDetails(manga: SManga) = Observable.fromCallable {
        manga.apply { initialized = true }
    }

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga) = Observable.fromCallable {
        SChapter.create().apply {
            name = "Gallery"
            url = manga.url.removeSuffix("/").plus("/attachment/1/")
            date_upload = runCatching {
                dateFormat.parse(dateCache[manga.url]!!)!!.time
            }.getOrDefault(0L)
        }.let(::listOf)
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val totalPages = document.selectFirst("#right_sidebar > h3, #title > h2")
            ?.text()?.trim()
            ?.run { pagesRegex.find(this)?.groupValues?.get(1) }
            ?.toIntOrNull()
            ?: return emptyList()

        val imgUrl = document.selectFirst("#display_image_detail img, #detail_list img")
            ?.absUrl("src")
            ?.replace("http://", "https://")
            ?.run { replace(hdRegex, "/") }
            ?: return emptyList()

        val imgUrlPrefix = imgUrl.substringBeforeLast("/")
        val ext = imgUrl.substringAfterLast(".")

        return (1..totalPages).map {
            Page(it, "", "$imgUrlPrefix/$it.$ext")
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private val tagNumRegex = Regex("""(\(\d+\))""")
        private val pagesRegex = Regex("""\d+/(\d+)${'$'}""")
        private val hdRegex = Regex("""(/p=\d+x?\d+?/)""")
        private val dateFormat by lazy {
            SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
        }
    }
}
