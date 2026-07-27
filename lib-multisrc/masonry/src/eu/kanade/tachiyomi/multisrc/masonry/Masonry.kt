package eu.kanade.tachiyomi.multisrc.masonry

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class Masonry : KeiSource() {

    protected open fun popularMangaUrl(page: Int): String = when (page) {
        1 -> baseUrl
        2 -> "$baseUrl/archive/"
        else -> "$baseUrl/archive/page/${page - 1}/"
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(popularMangaUrl(page)).asJsoup()
        return parseMangaList(document)
    }

    protected open fun parseMangaList(document: Document): MangasPage {
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

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/updates/sort/newest/mpage/$page/").asJsoup()
        return parseMangaList(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/search/post/".toHttpUrl().newBuilder()
                .addPathSegment(query.trim())
                .addEncodedPathSegments("mpage/$page/")
                .build()
        } else {
            val tagFilter = filters.firstInstanceOrNull<TagFilter>()
            val sortFilter = filters.firstInstanceOrNull<SortFilter>()!!

            baseUrl.toHttpUrl().newBuilder().apply {
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
        }

        val document = client.get(url).asJsoup()
        return parseMangaList(document)
    }

    /* Filters */

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val tags = client.get("$baseUrl/updates/sort/newest/").asJsoup()
            .select("#filter-a span:has(> input)")
            .map {
                Tag(
                    it.select("label").text(),
                    it.select("input").attr("value"),
                )
            }.let {
                listOf(Tag("", "")) + it
            }
        return tags.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<List<Tag>>()

        val filters = mutableListOf(
            Filter.Header("Filters ignored with text search"),
            Filter.Separator(),
            SortFilter(),
        )

        if (!filterData.isNullOrEmpty()) {
            filters += TagFilter(filterData)
        }

        return FilterList(filters)
    }

    /* Manga details & chapters */

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document, manga),
        )
    }

    protected open fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        document.selectFirst("p.link-btn")?.run {
            artist = select("a[href*=/model/]").eachText().joinToString()
            genre = select("a[href*=/tag/]").eachText().joinToString()
            author = selectFirst("a")?.text()
        }
        description = document.selectFirst("#content > p")?.text()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    protected open fun parseChapterList(document: Document, manga: SManga): List<SChapter> = listOf(
        SChapter.create().apply {
            name = "Gallery"
            url = manga.url
        },
    )

    /* Pages */
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".list-gallery a[href^=https://cdn.]").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("href"))
        }
    }

    protected fun Element.imgAttr(): String? = when {
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        else -> attr("abs:src")
    }
}
