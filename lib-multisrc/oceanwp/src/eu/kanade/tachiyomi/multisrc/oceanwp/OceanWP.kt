package eu.kanade.tachiyomi.multisrc.oceanwp

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class OceanWP : KeiSource() {

    override val supportsLatest = false

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(if (page > 1) "$baseUrl/page/$page/" else baseUrl).asJsoup()
        val mangas = document.select("article.blog-entry").map { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst("ul.page-numbers li a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("h2.blog-entry-title a")!!
        title = link.text()
        setUrlWithoutDomain(link.absUrl("href"))
        thumbnail_url = element.selectFirst("div.thumbnail img")?.absUrl("src")
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val categoryFilter = filters.filterIsInstance<CategoryFilter>().firstOrNull()
        val tagFilter = filters.filterIsInstance<TagFilter>().firstOrNull()

        val url = when {
            query.isNotEmpty() -> baseUrl.toHttpUrl().newBuilder().apply {
                addQueryParameter("s", query)
                if (page > 1) addPathSegments("page/$page/")
            }.build().toString()

            categoryFilter != null && categoryFilter.state > 0 -> {
                if (page > 1) "${categoryFilter.selected}page/$page/" else categoryFilter.selected
            }

            tagFilter != null && tagFilter.state > 0 -> {
                if (page > 1) "${tagFilter.selected}page/$page/" else tagFilter.selected
            }

            else -> baseUrl.toHttpUrl().newBuilder().apply {
                if (page > 1) addPathSegments("page/$page/")
            }.build().toString()
        }

        return searchMangaParse(client.get(url).asJsoup())
    }

    private fun searchMangaParse(document: Document): MangasPage {
        val mangas = document.select("article").map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst("ul.page-numbers li a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("h2.search-entry-title a, h2.blog-entry-title a")!!
        title = link.text()
        setUrlWithoutDomain(link.absUrl("href"))
        thumbnail_url = element.selectFirst("div.thumbnail img")?.absUrl("src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.none { it.isNotEmpty() }) return null

        return mangaDetailsParse(client.get(url).asJsoup()).apply { setUrlWithoutDomain(url.toString()) }
    }

    // Details
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) mangaDetailsParse(client.get(getMangaUrl(manga)).asJsoup()) else manga
        val chapter = SChapter.create().apply {
            name = "Chapter 1"
            setUrlWithoutDomain(manga.url)
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val content = document.selectFirst("div#content") ?: document
        title = content.selectFirst(".entry-title")!!.text()
        description = content.selectFirst("div.entry-content")?.text()
        genre = content.select("li.meta-cat a, li.meta-category a").joinToString { it.text() }
        thumbnail_url = content.selectFirst("div.thumbnail img")?.absUrl("src")
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        status = SManga.COMPLETED
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.entry-content img").mapIndexed { i, img ->
            val url = img.absUrl("src")
            Page(i, document.location(), url)
        }
    }

    // Filters
    protected open val hasTagFilter = true

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl).asJsoup()
        val categories = document.select("ul.sub-menu li[class*=menu-item-type-taxonomy] a").map {
            Pair(it.text(), it.absUrl("href"))
        }
        val tags = if (hasTagFilter) {
            document.select("div.tagcloud a").map {
                Pair(it.text(), it.absUrl("href"))
            }
        } else {
            emptyList()
        }
        return FilterData(categories, tags).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList()
        val categoryList = listOf(Pair("Default", "")) + filterData.categories
        val tagList = listOf(Pair("Default", "")) + filterData.tags

        val filters = mutableListOf<Filter<*>>()
        filters.add(Filter.Header("Filter tidak bisa dikombinasikan dengan pencarian teks"))

        if (categoryList.size > 1 && tagList.size > 1) {
            filters.add(Filter.Header("Filter di bawah ini tidak bisa dikombinasikan satu sama lain"))
        }

        filters.add(Filter.Separator())

        if (categoryList.size > 1) {
            filters.add(CategoryFilter(categoryList))
        }

        if (tagList.size > 1 && hasTagFilter) {
            filters.add(TagFilter(tagList))
        }

        return FilterList(filters)
    }
}
