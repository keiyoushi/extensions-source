package eu.kanade.tachiyomi.extension.en.philiascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PhiliaScans :
    Madara(
        "Philia Scans",
        "https://philiascans.org",
        "en",
    ) {
    override val versionId: Int = 3

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("post_type", "wp-manga")
            .addQueryParameter("s", "")
            .addQueryParameter("sort", "most_viewed")
            .addQueryParameter("paged", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaSelector() = ".original .unit"
    override val popularMangaUrlSelector = ".info a.c-title"
    override val popularMangaUrlSelectorImg = ".poster img:not(.flag-icon)"
    override fun popularMangaNextPageSelector() = ".pagination li:not(.disabled) .page-link[rel=next]"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/recently-updated/?page=$page", headers)

    override fun searchRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/${searchPage(page)}".toHttpUrl().newBuilder()
        url.addQueryParameter("post_type", "wp-manga")
        url.addQueryParameter("s", query)

        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> {
                    filter.state.filter { it.state }.forEachIndexed { index, item ->
                        url.addQueryParameter("type[$index]", item.value)
                    }
                }

                is YearFilter -> {
                    filter.state.filter { it.state }.forEachIndexed { index, item ->
                        url.addQueryParameter("release[$index]", item.value)
                    }
                }

                is OrderByFilter -> {
                    url.addQueryParameter("sort", filter.toUriPart())
                }

                is GenreConditionFilter -> {
                    if (filter.toUriPart().isNotEmpty()) {
                        url.addQueryParameter("genre_mode", filter.toUriPart())
                    }
                }

                is GenreList -> {
                    filter.state.filter { it.state }.forEachIndexed { index, item ->
                        url.addQueryParameter("genre[$index]", item.id)
                    }
                }

                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override val mangaDetailsSelectorTitle = "h1.serie-title"
    override val mangaDetailsSelectorAuthor = ".stat-item:has(.stat-label:contains(Author)) .stat-value"
    override val mangaDetailsSelectorArtist = ".stat-item:has(.stat-label:contains(Artist)) .stat-value"
    override val mangaDetailsSelectorStatus = ".stat-item:has(.stat-label:contains(Status)) .manga"
    override val mangaDetailsSelectorGenre = "div.genre-list a"
    override val mangaDetailsSelectorDescription = "div.description-content"
    override val mangaDetailsSelectorThumbnail = ".main-cover .cover"
    override val altNameSelector = "h6.alternative-title"
    override val seriesTypeSelector = ".stat-item:has(.stat-label:contains(Type)) .manga"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)
        manga.status = parseStatus(document.selectFirst(mangaDetailsSelectorStatus)?.text())
        return manga
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("Releasing", true) -> SManga.ONGOING
        status.contains("Completed", true) -> SManga.COMPLETED
        status.contains("On Hold", true) -> SManga.ON_HIATUS
        status.contains("Canceled", true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "li.item:not(:has(a[href='#'])):not(:has(.fa-coins))"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val urlElement = element.selectFirst("a")!!
        setUrlWithoutDomain(urlElement.absUrl("href"))
        name = element.selectFirst("zebi")!!.text()
    }

    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = if (fromSearch) {
        url?.replace(
            // try to resolve actual cover from thumbnail, usually has -280x400 suffix
            "-280x400.",
            ".",
        )
    } else {
        url
    }

    override val pageListParseSelector = "div#ch-images img"

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("NOTE: Filters are applied when you search."),
            Filter.Separator(),
            TypeFilter(),
            Filter.Separator(),
            OrderByFilter(
                intl["order_by_filter_title"],
                orderByFilterOptions.toList(),
                0,
            ),
            Filter.Separator(),
        )

        if (yearsList.isNotEmpty()) {
            filters.add(YearFilter("Year", yearsList))
            filters.add(Filter.Separator())
        }

        if (genresList.isNotEmpty()) {
            filters += listOf(
                Filter.Separator(),
                GenreConditionFilter(
                    intl["genre_condition_filter_title"],
                    genreConditionFilterOptions.toList(),
                ),
                Filter.Separator(),
                GenreList(
                    intl["genre_filter_title"],
                    genresList,
                ),
            )
        } else if (fetchGenres) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_missing_warning"]),
            )
        }

        return FilterList(filters)
    }

    override fun genresRequest(): Request = GET("$baseUrl/?post_type=wp-manga&s=", headers)

    override fun parseGenres(document: Document): List<Genre> {
        yearsList = document.select("input[name='release[]']").mapNotNull {
            val value = it.attr("value")
            val label = it.nextElementSibling()?.text() ?: value
            Pair(label, value)
        }

        return document.select("ul.genres li").mapNotNull {
            val label = it.selectFirst("label")?.text() ?: return@mapNotNull null
            val value = it.selectFirst("input")?.attr("value") ?: return@mapNotNull null
            Genre(label, value)
        }
    }

    private var yearsList: List<Pair<String, String>> = emptyList()

    private class CheckBoxVal(name: String, val value: String) : Filter.CheckBox(name)

    private class TypeFilter :
        Filter.Group<CheckBoxVal>(
            "Type",
            listOf(
                Pair("Manga", "manga"),
                Pair("Manhua", "manhua"),
                Pair("Manhwa", "manhwa"),
                Pair("Seinen", "seinen"),
            ).map { CheckBoxVal(it.first, it.second) },
        )

    private class YearFilter(title: String, years: List<Pair<String, String>>) :
        Filter.Group<CheckBoxVal>(
            title,
            years.map { CheckBoxVal(it.first, it.second) },
        )

    override val orderByFilterOptions: Map<String, String> = mapOf(
        intl["order_by_filter_relevance"] to "",
        intl["order_by_filter_new"] to "recently_added",
        intl["order_by_filter_az"] to "title_az",
        intl["order_by_filter_views"] to "most_viewed",
    )

    override val genreConditionFilterOptions: Map<String, String> = mapOf(
        intl["genre_condition_filter_or"] to "",
        intl["genre_condition_filter_and"] to "and",
    )
}
