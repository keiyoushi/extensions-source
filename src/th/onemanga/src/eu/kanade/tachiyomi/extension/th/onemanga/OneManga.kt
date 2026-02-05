package eu.kanade.tachiyomi.extension.th.onemanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class OneManga :
    MangaThemesia(
        "One-Manga",
        "https://one-manga.com",
        "th",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Bangkok")
        },
    ) {
    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        // Add 'color' badge as a genre
        if (document.selectFirst(".thumb .colored") != null) {
            genre = genre?.plus(", Color")
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(mangaUrlDirectory.substring(1))
            .addQueryParameter("s", query)
            .addQueryParameter("page", page.toString())

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
}
