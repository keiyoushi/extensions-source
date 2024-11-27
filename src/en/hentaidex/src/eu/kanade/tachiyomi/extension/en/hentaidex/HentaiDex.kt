package eu.kanade.tachiyomi.extension.en.hentaidex

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiDex : MangaThemesia(
    "HentaiDex",
    "https://dexhentai.com",
    "en",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US),
    mangaUrlDirectory = "/title",
) {
    override fun chapterListParse(response: Response): List<SChapter> =
        super.chapterListParse(response).sortedByDescending { it.chapter_number }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".chapternum").text().ifBlank { urlElements.first()!!.text() }
        chapter_number = element.attr("data-num").toFloat()
        date_upload = element.selectFirst(".chapterdate")?.text().parseChapterDate()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()

        // Can't use filter if is a global search
        if (query.isNotEmpty()) {
            url.addQueryParameter("s", query)
            return GET(url.build(), headers)
        }

        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }

                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }

                is StatusFilter -> {
                    url.addQueryParameter("status", filter.selectedValue())
                }

                is TypeFilter -> {
                    url.addQueryParameter("type", filter.selectedValue())
                }

                is OrderByFilter -> {
                    url.addQueryParameter("order", filter.selectedValue())
                }

                is GenreListFilter -> {
                    filter.state
                        .filterNot { it.state == Filter.TriState.STATE_IGNORE }
                        .forEach {
                            val genre = when (it.state) {
                                Filter.TriState.STATE_EXCLUDE -> "-${it.value}"
                                else -> it.value
                            }

                            url.addQueryParameter("genre[]", genre)
                        }
                }
                // if site has project page, default value "hasProjectPage" = false
                is ProjectFilter -> {
                    if (filter.selectedValue() == "project-filter-on") {
                        url.setPathSegment(0, projectPageString.substring(1))
                    }
                }

                else -> { /* Do Nothing */
                }
            }
        }
        url.addPathSegment(mangaUrlDirectory.substring(1))
            .addQueryParameter("page", page.toString())
        return GET(url.build(), headers)
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Text search ignores filters"),
            Filter.Separator(),
            AuthorFilter(intl["author_filter_title"]),
            YearFilter(intl["year_filter_title"]),
            StatusFilter(intl["status_filter_title"], statusOptions),
            TypeFilter(intl["type_filter_title"], typeFilterOptions),
            OrderByFilter(intl["order_by_filter_title"], orderByFilterOptions),
        )
        if (!genrelist.isNullOrEmpty()) {
            filters.addAll(
                listOf(
                    Filter.Header(intl["genre_exclusion_warning"]),
                    GenreListFilter(intl["genre_filter_title"], getGenreList()),
                ),
            )
        } else {
            filters.add(
                Filter.Header(intl["genre_missing_warning"]),
            )
        }
        if (hasProjectPage) {
            filters.addAll(
                mutableListOf<Filter<*>>(
                    Filter.Separator(),
                    Filter.Header(intl["project_filter_warning"]),
                    Filter.Header(intl.format("project_filter_name", name)),
                    ProjectFilter(intl["project_filter_title"], projectFilterOptions),
                ),
            )
        }
        return FilterList(filters)
    }
}
