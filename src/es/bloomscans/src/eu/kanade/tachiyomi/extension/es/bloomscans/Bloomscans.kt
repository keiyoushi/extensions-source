package eu.kanade.tachiyomi.extension.es.bloomscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class Bloomscans :
    MangaThemesia(
        "Bloom Scans",
        "https://bloomscans.com",
        "es",
        mangaUrlDirectory = "/series",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es")),
    ) {

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("page")
            .addPathSegment(page.toString())
            .addQueryParameter("s", query)

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
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .forEach {
                            val value = if (it.state == Filter.TriState.STATE_EXCLUDE) "-${it.value}" else it.value
                            url.addQueryParameter("genre[]", value)
                        }
                }

                // if site has project page, default value "hasProjectPage" = false
                is ProjectFilter -> {
                    if (filter.selectedValue() == "project-filter-on") {
                        url.setPathSegment(0, projectPageString.substring(1))
                    }
                }

                else -> { /* Do Nothing */ }
            }
        }
        url.addPathSegment("")
        return GET(url.build(), headers)
    }

    override val seriesTitleSelector = ".lrs-title"
    override val seriesThumbnailSelector = "img.lrs-cover"
    override val seriesDescriptionSelector = ".lrs-syn-wrap"
    override val seriesStatusSelector = ".lrs-infotable tr:contains(Status) td:last-child"
    override val seriesGenreSelector = ".lrs-genre"

    override fun chapterListSelector() = "#lrs-native-chapterlist li"
}
