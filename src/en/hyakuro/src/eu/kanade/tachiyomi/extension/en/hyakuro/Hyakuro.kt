package eu.kanade.tachiyomi.extension.en.hyakuro

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Hyakuro : KeiSource() {

    private val apiUrl get() = "$baseUrl/backend/api"

    // Popular/A-Z
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("populate", "Cover,Chapters")
            .addQueryParameter("sort", "Title:asc")
            .addQueryParameter("pagination[page]", page.toString())
            .build()
        return parseMangaList(client.get(url).parseAs<PaginatedResponse>())
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("populate", "Cover,Chapters")
            .addQueryParameter("sort", "updatedAt:desc")
            .addQueryParameter("pagination[page]", page.toString())
            .build()
        return parseMangaList(client.get(url).parseAs<PaginatedResponse>())
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("pagination[page]", page.toString())
            addQueryParameter("populate", "Cover,Chapters")
            addQueryParameter("sort", "updatedAt:desc")

            if (query.isNotBlank()) {
                addQueryParameter("filters[Title][\$containsi]", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> {
                        if (filter.state != 0) {
                            val status = filter.values[filter.state]
                            if (status == "Oneshot") {
                                addQueryParameter("filters[Oneshot][\$eq]", "true")
                            } else {
                                addQueryParameter("filters[Status][\$eq]", status)
                            }
                        }
                    }

                    is CategoryFilter -> {
                        filter.state.filter { it.state }.forEachIndexed { index, checkbox ->
                            addQueryParameter("filters[\$and][${index + 1}][Categories][\$containsi]", checkbox.name)
                        }
                    }

                    else -> {}
                }
            }
        }.build()
        return parseMangaList(client.get(url).parseAs<PaginatedResponse>())
    }

    private fun parseMangaList(result: PaginatedResponse): MangasPage {
        val mangas = result.data.map { it.attributes.toSManga(baseUrl) }
        val hasNextPage = result.meta.pagination.page < result.meta.pagination.pageCount
        return MangasPage(mangas, hasNextPage)
    }

    // Details and chapters come from the same response
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfter("/manga/")
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("filters[slug][\$eq]", slug)
            .addQueryParameter("populate", "Cover,Chapters")
            .build()
        val attributes = client.get(url).parseAs<PaginatedResponse>().data.first().attributes
        return SMangaUpdate(
            attributes.toSManga(baseUrl),
            attributes.chapters!!.sortedByDescending { it.chapter }
                .map { it.toSChapter(slug, attributes) },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.split("#")
        val slug = parts[0]
        val chapterNumber = parts[1]
        return "$baseUrl/manga/$slug/read/$chapterNumber/1"
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val parts = chapter.url.split("#")
        val slug = parts[0]
        val chapterId = parts[2].toInt()
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("filters[slug][\$eq]", slug)
            .addQueryParameter("populate[Chapters][populate]", "*")
            .build()
        val attributes = client.get(url).parseAs<PaginatedResponse>().data.first().attributes
        val chapterData = attributes.chapters!!.find { it.id == chapterId }!!

        return chapterData.pages!!.data
            .sortedBy { it.attributes.url }
            .mapIndexed { index, pageData ->
                Page(index, imageUrl = "$baseUrl/backend${pageData.attributes.url}")
            }
    }

    // Filters
    private class StatusFilter : Filter.Select<String>("Status", arrayOf("All", "Ongoing", "Completed", "Dropped", "Oneshot"))
    private class Category(name: String) : Filter.CheckBox(name)
    private class CategoryFilter(categories: List<Category>) : Filter.Group<Category>("Categories", categories)

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("NOTE: Search query will be applied to filters"),
        StatusFilter(),
        CategoryFilter(getCategoryList()),
    )

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
