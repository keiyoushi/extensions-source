package eu.kanade.tachiyomi.extension.en.readkomik

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class ReadKomik : MangaThemesia(
    "Readkomik",
    "https://rkreader.org",
    "en",
    "/archives/manga",
) {
    override val versionId = 2

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val hasProjectPage = true

    override fun popularMangaRequest(page: Int) =
        searchMangaRequest(page, "", FilterList(OrderByFilter("", orderByFilterOptions, "az-list")))

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = ".listupd .utao .uta .imgu"

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesParse(response: Response) =
        super.latestUpdatesParse(response).copy(hasNextPage = false)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url.apply {
                addPathSegment("page")
                addPathSegment("$page")
                addQueryParameter("s", query)
            }
            return GET(url.build(), headers)
        }

        for (filter in filters) {
            if (filter !is SelectFilter) continue

            val selectedValue = filter.selectedValue()
            if (selectedValue == "none") {
                continue
            }

            when (filter) {
                is GenreFilter -> {
                    url.apply {
                        addPathSegment(mangaUrlDirectory.substringBeforeLast("/").substring(1))
                        addPathSegment("genres")
                        addPathSegment(selectedValue)
                    }
                    break
                }

                is OrderByFilter -> {
                    url.addPathSegment("az-list")
                    if (selectedValue == "az-list") break

                    url.addQueryParameter("show", selectedValue)

                    break
                }

                is ProjectFilter -> {
                    if (selectedValue == "project-filter-on") {
                        url.setPathSegment(0, projectPageString.substring(1))
                        break
                    }
                }
                else -> { }
            }
        }

        url.apply {
            addPathSegment("page")
            addPathSegment("$page")
        }

        return GET(url.build(), headers)
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Multiple filter selection not supported"),
            OrderByFilter("Title", orderByFilterOptions),
            Filter.Header("Or"),
            GenreFilter("Genre", genreFilterOptions),
        )

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

    override val orderByFilterOptions = arrayOf("None" to "none", "A-Z" to "az-list").let {
        val A = 65; val Z = A + 25
        it.plus((A..Z).map { "${it.toChar()}" }.map { it to it }.toTypedArray())
    }

    private val genres = listOf(
        "None", "Action", "Adult", "Adventure", "Comedy",
        "Drama", "Ecchi", "Fantasy", "Harem", "Historical",
        "Horror", "Josei", "Martial Arts", "Mature", "Romance",
        "School Life", "Sci-fi", "Seinen", "Shounen", "Slice of Life",
        "Smut", "Supernatural", "Tragedy", "Yaoi",
    )

    private var genreFilterOptions: Array<Pair<String, String>> = genres
        .map { Pair(it, it.lowercase().replace(" ", "-")) }
        .toTypedArray()

    protected class GenreFilter(
        name: String,
        options: Array<Pair<String, String>>,
    ) : SelectFilter(
        name,
        options,
    )
}
