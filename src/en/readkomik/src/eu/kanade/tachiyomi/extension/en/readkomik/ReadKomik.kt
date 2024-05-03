package eu.kanade.tachiyomi.extension.en.readkomik

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
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
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    // mangaDetailsRequest: Keeps compatibility with before version
    override fun mangaDetailsRequest(manga: SManga) =
        super.mangaDetailsRequest(replaceBaseUrlDirectoryByCustomUrlDirectory(manga))

    // chapterListRequest: Keeps compatibility with before version
    override fun chapterListRequest(manga: SManga) =
        super.chapterListRequest(replaceBaseUrlDirectoryByCustomUrlDirectory(manga))

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

        val filter = filters.filterIsInstance<SelectFilter>()
            .first { it.selectedValue().isNotBlank() }

        when (filter) {
            is GenreFilter -> {
                url.apply {
                    addPathSegment(mangaUrlDirectory.substringBeforeLast("/").substring(1))
                    addPathSegment("genres")
                    addPathSegment(filter.selectedValue())
                }
            }

            is OrderByFilter -> {
                url.addPathSegment("az-list")
                if (filter.selectedValue() != "az-list") {
                    url.addQueryParameter("show", filter.selectedValue())
                }
            }

            is ProjectFilter -> {
                url.setPathSegment(0, projectPageString.substring(1))
            }

            else -> { /* Do Nothing */ }
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

        filters.addAll(
            mutableListOf<Filter<*>>(
                Filter.Separator(),
                Filter.Header(intl["project_filter_warning"]),
                Filter.Header(intl.format("project_filter_name", name)),
                ProjectFilter(intl["project_filter_title"], projectFilterOptions),
            ),
        )

        return FilterList(filters)
    }

    override val orderByFilterOptions = arrayOf("None" to "", "A-Z" to "az-list").let {
        val A = 65; val Z = A + 25
        it.plus((A..Z).map { "${it.toChar()}" }.map { it to it }.toTypedArray())
    }

    private val genres = listOf(
        "Action", "Adult", "Adventure", "Comedy",
        "Drama", "Ecchi", "Fantasy", "Harem", "Historical",
        "Horror", "Josei", "Martial Arts", "Mature", "Romance",
        "School Life", "Sci-fi", "Seinen", "Shounen", "Slice of Life",
        "Smut", "Supernatural", "Tragedy", "Yaoi",
    )

    private var genreFilterOptions: Array<Pair<String, String>> = arrayOf(("None" to "")).let {
        it.plus(genres.map { Pair(it, it.lowercase().replace(" ", "-")) }.toTypedArray())
    }

    private fun replaceBaseUrlDirectoryByCustomUrlDirectory(manga: SManga): SManga {
        return when {
            isOldUrl(manga) -> manga.apply {
                url = url.replace("/manga", mangaUrlDirectory)
            }
            else -> manga
        }
    }

    private fun isOldUrl(manga: SManga) = !manga.url.contains(this.mangaUrlDirectory)

    protected class GenreFilter(
        name: String,
        options: Array<Pair<String, String>>,
    ) : SelectFilter(
        name,
        options,
    )
}
