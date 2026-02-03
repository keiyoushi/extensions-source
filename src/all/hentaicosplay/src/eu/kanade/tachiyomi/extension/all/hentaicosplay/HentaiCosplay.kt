package eu.kanade.tachiyomi.extension.all.hentaicosplay

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

    override val baseUrl = "https://hentai-cosplay-xxx.com"

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

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking/page/$page/", headers)

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
                        ?.absUrl("src")
                        ?.replace("http://", "https://")
                    title = element.selectFirst("span:not(.posted)")!!.text()
                    element.selectFirst("span.posted")
                        ?.text()?.also { dateCache[url] = it }
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
                }
            }
        val hasNextPage = document.selectFirst("div.wp-pagenavi > a[rel=next]") != null

        return MangasPage(entries, hasNextPage)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        fetchFilters()
        return super.fetchLatestUpdates(page)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/recently/page/$page/", headers)

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

                    else -> {}
                }
            }

            return GET("$baseUrl/search/page/$page/", headers)
        }
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    private var tagCache: List<Pair<String, String>> = emptyList()

    private fun fetchFilters() {
        if (tagCache.isEmpty()) fetchTags()
    }

    private fun fetchTags() {
        Single.fromCallable {
            runCatching {
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
        }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe()
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

    override fun getFilterList(): FilterList = if (tagCache.isEmpty()) {
        FilterList(Filter.Header("Press reset to attempt to load filters"))
    } else {
        FilterList(
            Filter.Header("Ignored with text search"),
            Filter.Separator(),
            TagFilter("Ranked Tags", tagCache),
        )
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            genre = document.select("#detail_tag a[href*=/tag/]").eachText().joinToString()
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.COMPLETED
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        SChapter.create().apply {
            name = "Gallery"
            url = manga.url.replace("/image/", "/story/")
            date_upload = runCatching {
                dateFormat.parse(dateCache[manga.url]!!)!!.time
            }.getOrDefault(0L)
        }.let(::listOf)
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("amp-img[src*=upload]:not(.related-thumbnail)")
            .mapIndexed { index, element ->
                Page(
                    index = index,
                    imageUrl = element.attr("src"),
                )
            }
    }

    override fun imageUrlParse(response: Response) = imageUrlParse(response.asJsoup())

    private fun imageUrlParse(document: Document): String = document.selectFirst("#display_image_detail img, #detail_list img")!!
        .absUrl("src")
        .replace("http://", "https://")
        .replace(hdRegex, "/")

    companion object {
        private val tagNumRegex = Regex("""(\(\d+\))""")
        private val pagesRegex = Regex("""\d+/(\d+)${'$'}""")
        private val hdRegex = Regex("""(/p=\d+x?\d+?/)""")
        private val dateFormat by lazy {
            SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
        }
    }
}
