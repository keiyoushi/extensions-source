package eu.kanade.tachiyomi.extension.en.hyakuro

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class Hyakuro : HttpSource() {
    override val name = "Hyakuro Translations"
    override val baseUrl = "https://hyakuro.net"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/backend/api"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular/A-Z
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("populate", "Cover,Chapters")
            .addQueryParameter("sort", "Title:asc")
            .addQueryParameter("pagination[page]", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<PaginatedResponse>()
        val mangas = result.data.map { it.attributes.toSManga(baseUrl) }
        val hasNextPage = result.meta.pagination.page < result.meta.pagination.pageCount
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("populate", "Cover,Chapters")
            .addQueryParameter("sort", "updatedAt:desc")
            .addQueryParameter("pagination[page]", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
        url.addQueryParameter("pagination[page]", page.toString())
        url.addQueryParameter("populate", "Cover,Chapters")
        url.addQueryParameter("sort", "updatedAt:desc")

        if (query.isNotBlank()) {
            url.addQueryParameter("filters[Title][\$containsi]", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> {
                    if (filter.state != 0) {
                        val status = filter.values[filter.state]
                        if (status == "Oneshot") {
                            url.addQueryParameter("filters[Oneshot][\$eq]", "true")
                        } else {
                            url.addQueryParameter("filters[Status][\$eq]", status)
                        }
                    }
                }
                is CategoryFilter -> {
                    filter.state.filter { it.state }.forEachIndexed { index, checkbox ->
                        url.addQueryParameter("filters[\$and][${index + 1}][Categories][\$containsi]", checkbox.name)
                    }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details
    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("/manga/")
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("filters[slug][\$eq]", slug)
            .addQueryParameter("populate", "Cover,Chapters")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<PaginatedResponse>()
        return result.data.first().attributes.toSManga(baseUrl)
    }

    // Chapter
    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaSlug = response.request.url.queryParameter("filters[slug][\$eq]")!!
        val parent = response.parseAs<PaginatedResponse>().data.first().attributes
        return parent.chapters!!
            .sortedByDescending { it.chapter }
            .map { it.toSChapter(mangaSlug, parent) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.split("#")
        val slug = parts[0]
        val chapterNumber = parts[1]
        return "$baseUrl/manga/$slug/read/$chapterNumber/1"
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val parts = chapter.url.split("#")
        val slug = parts[0]
        val chapterId = parts[2]
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("filters[slug][\$eq]", slug)
            .addQueryParameter("populate[Chapters][populate]", "*")
            .fragment(chapterId)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterId = response.request.url.fragment!!.toInt()
        val parent = response.parseAs<PaginatedResponse>().data.first().attributes
        val chapter = parent.chapters!!.find { it.id == chapterId }!!

        return chapter.pages!!.data
            .sortedBy { it.attributes.url }
            .mapIndexed { index, pageData ->
                Page(index, imageUrl = "$baseUrl/backend${pageData.attributes.url}")
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters
    private class StatusFilter : Filter.Select<String>("Status", arrayOf("All", "Ongoing", "Completed", "Dropped", "Oneshot"))
    private class Category(name: String) : Filter.CheckBox(name)
    private class CategoryFilter(categories: List<Category>) : Filter.Group<Category>("Categories", categories)

    override fun getFilterList(): FilterList {
        Filter.Header("NOTE: Search query will be applied to filters")
        return FilterList(
            StatusFilter(),
            CategoryFilter(getCategoryList()),
        )
    }

    private fun getCategoryList() = listOf(
        Category("Action"),
        Category("Adult"),
        Category("Adventure"),
        Category("Comedy"),
        Category("Doujinshi"),
        Category("Drama"),
        Category("Ecchi"),
        Category("Fantasy"),
        Category("Gender Bender"),
        Category("Harem"),
        Category("Hentai"),
        Category("Historical"),
        Category("Horror"),
        Category("Josei"),
        Category("Lolicon"),
        Category("Martial Arts"),
        Category("Mature"),
        Category("Mecha"),
        Category("Mystery"),
        Category("Psychological"),
        Category("Romance"),
        Category("School Life"),
        Category("Sci-fi"),
        Category("Seinen"),
        Category("Shotacon"),
        Category("Shoujo"),
        Category("Shoujo Ai"),
        Category("Shounen"),
        Category("Shounen Ai"),
        Category("Slice of Life"),
        Category("Smut"),
        Category("Sports"),
        Category("Supernatural"),
        Category("Tragedy"),
        Category("Webtoon"),
        Category("Yaoi"),
        Category("Yuri"),
    )
}
