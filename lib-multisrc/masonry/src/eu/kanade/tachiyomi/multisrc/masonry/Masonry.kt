package eu.kanade.tachiyomi.multisrc.masonry

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
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

abstract class Masonry(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = when (page) {
            1 -> baseUrl
            2 -> "$baseUrl/archive/"
            else -> "$baseUrl/archive/page/${page - 1}/"
        }
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        getTags()
        val document = response.asJsoup()
        val mangas = document.select(".list-gallery:not(.static) figure:not(:has(a[href*=/video/]))")
            .map { element ->
                SManga.create().apply {
                    element.selectFirst("a")!!.also {
                        setUrlWithoutDomain(it.absUrl("href"))
                        title = it.attr("title")
                    }
                    thumbnail_url = element.selectFirst("img")?.imgAttr()
                }
            }
        val hasNextPage = document.selectFirst(".pagination-a li.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/updates/sort/newest/mpage/$page/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotEmpty()) {
        val url = "$baseUrl/search/post/".toHttpUrl().newBuilder()
            .addPathSegment(query.trim())
            .addEncodedPathSegments("mpage/$page/")
            .build()
        GET(url, headers)
    } else {
        val tagFilter = filters.firstInstanceOrNull<TagFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()!!

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (tagFilter == null || tagFilter.selected == "") {
                addPathSegment("updates")
                sortFilter.getUriPartIfNeeded("search").also {
                    if (it.isBlank()) {
                        addEncodedPathSegments("page/$page/")
                    } else {
                        addEncodedPathSegments(it)
                        addEncodedPathSegments("mpage/$page/")
                    }
                }
            } else {
                addPathSegment("tag")
                addPathSegment(tagFilter.selected)
                sortFilter.getUriPartIfNeeded("tag").also {
                    if (it.isBlank()) {
                        addEncodedPathSegments("page/$page/")
                    } else {
                        addEncodedPathSegments(it)
                        addEncodedPathSegments("mpage/$page/")
                    }
                }
            }
        }.build()

        GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    private var tags = emptyList<Pair<String, String>>()
    private var tagsFetchAttempt = 0

    private fun getTags() {
        if (tags.isEmpty() && tagsFetchAttempt < 3) {
            runCatching {
                tags = client.newCall(GET("$baseUrl/updates/sort/newest/", headers))
                    .execute().asJsoup()
                    .select("#filter-a span:has(> input)")
                    .mapNotNull {
                        Pair(
                            it.select("label").text(),
                            it.select("input").attr("value"),
                        )
                    }.let {
                        listOf(Pair("", "")) + it
                    }
            }
            tagsFetchAttempt++
        }
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf(
            Filter.Header("Filters ignored with text search"),
            Filter.Separator(),
            SortFilter(),
        )

        if (tags.isEmpty()) {
            filters.add(
                Filter.Header("Press 'reset' to attempt to load tags"),
            )
        } else {
            filters.add(
                TagFilter(tags),
            )
        }

        return FilterList(filters)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            document.selectFirst("p.link-btn")?.run {
                artist = select("a[href*=/model/]").eachText().joinToString()
                genre = select("a[href*=/tag/]").eachText().joinToString()
                author = selectFirst("a")?.text()
            }
            description = document.selectFirst("#content > p")?.text()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.just(
        listOf(
            SChapter.create().apply {
                name = "Gallery"
                url = manga.url
            },
        ),
    )

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".list-gallery a[href^=https://cdn.]").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("href"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    protected fun Element.imgAttr(): String? = when {
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        else -> attr("abs:src")
    }
}
