package eu.kanade.tachiyomi.extension.id.mangacan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class MangaCan : MangaThemesia() {
    override val mangaUrlDirectory = ""
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override val supportsLatest = false

    override val seriesGenreSelector = ".seriestugenre a[href*=genre]"

    override val pageSelector = "div.images img"

    override fun searchMangaUrl(page: Int, query: String, filters: FilterList) = baseUrl.toHttpUrl().newBuilder().apply {
        val selected = filters.filterIsInstance<GenreFilter>()
            .firstOrNull { it.selectedValue().isNotBlank() }?.selectedValue().orEmpty()

        if (query.isNotBlank()) {
            addPathSegment("cari")
            addPathSegment(query.trim().replace(SPACES_REGEX, "-").lowercase())
            addPathSegment("$page.html")
        } else {
            addPathSegments(selected)
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreData>>()

        val filters = mutableListOf<Filter<*>>()
        if (!genres.isNullOrEmpty()) {
            filters.addAll(
                listOf(
                    Filter.Header(intl["genre_exclusion_warning"]),
                    GenreFilter(intl["genre_filter_title"], genres.map { it.name to it.value }.toTypedArray()),
                ),
            )
        }
        return FilterList(filters)
    }

    override fun parseGenres(document: Document): List<GenreData> = mutableListOf(GenreData("All", "")).apply {
        this += document.select(".textwidget.custom-html-widget a").map { element ->
            GenreData(element.text(), element.attr("href"))
        }
    }

    private class GenreFilter(
        name: String,
        options: Array<Pair<String, String>>,
    ) : SelectFilter(
        name,
        options,
    )

    companion object {
        val SPACES_REGEX = "\\s+".toRegex()
    }
}
