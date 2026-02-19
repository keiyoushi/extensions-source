package eu.kanade.tachiyomi.extension.en.heytoon

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale

class HeyToon : HttpSource() {
    override val name = "HeyToon"
    override val baseUrl = "https://heytoon.net"
    override val lang = "en"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override val client = network.cloudflareClient

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = if (page == 1) {
        super.fetchPopularManga(page)
    } else {
        fetchSearchManga(page - 1, "", SortFilter.popular)
    }

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select("section[class*=slider]:has(h2:matches((?i)popular|trending)) a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.text()
                thumbnail_url = element.selectFirst("img[alt!=badge]")
                    ?.absUrl("data-src")
            }
        }

        return MangasPage(entries, hasNextPage = true)
    }

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", SortFilter.latest)

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = if (query.isNotEmpty()) {
        querySearch(query)
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Doesn't work with text search"),
        Filter.Separator(),
        SortFilter(),
        GenreFilter(),
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/en/genres".toHttpUrl().newBuilder().apply {
            filters.firstInstanceOrNull<GenreFilter>()?.selected?.also { genre ->
                addPathSegment(genre)
            }
            filters.firstInstance<SortFilter>().sort.also { sort ->
                addQueryParameter("orderBy", sort)
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select("div[class*=comicItem] a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                with(element.selectFirst("img[alt!=badge]")!!) {
                    title = attr("title")
                    thumbnail_url = absUrl("data-src")
                }
            }
        }

        val hasNextPage = document.selectFirst(".wp-pagenavi .nextpostslink") != null

        return MangasPage(entries, hasNextPage)
    }

    private fun querySearch(query: String): Observable<MangasPage> {
        val url = "$baseUrl/api/complete-search".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .build()

        val ajaxHeaders = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        return client.newCall(GET(url, ajaxHeaders))
            .asObservableSuccess()
            .map {
                val data = it.parseAs<List<Comic>>()

                val entries = data.map { comic ->
                    SManga.create().apply {
                        setUrlWithoutDomain(comic.url)
                        title = comic.title
                        thumbnail_url = comic.cover
                    }
                }

                MangasPage(entries, hasNextPage = false)
            }
    }

    @Serializable
    class Comic(
        @SerialName("linkComic") val url: String,
        val title: String,
        @SerialName("raw_thumb") val cover: String? = null,
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("#titleSubWrapper h1.titCon")!!.text()
            description = document.selectFirst("#modal_detail .cont_area p")?.text()
            genre = document.select("#modal_detail a[href*=genres]").eachText().joinToString()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            status = with(document.select(".badgeArea span").eachText()) {
                if (contains("Up")) {
                    SManga.ONGOING
                } else if (contains("Completed")) {
                    SManga.COMPLETED
                } else {
                    SManga.UNKNOWN
                }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".episodeListConPC a#episodeItemCon").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                name = it.selectFirst(".comicInfo p.episodeStitle")!!.text()
                date_upload = dateFormat.tryParse(it.selectFirst(".comicInfo .episodeDate")?.text())
            }
        }.asReversed()
    }

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("#comicContent img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
