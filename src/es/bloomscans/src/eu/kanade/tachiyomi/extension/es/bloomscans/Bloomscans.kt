package eu.kanade.tachiyomi.extension.es.bloomscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
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
        if (query.isNotEmpty() && filters.isEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("page")
                .addPathSegment(page.toString())
                .addQueryParameter("s", query)
                .addPathSegment("")
            return GET(url.build(), headers)
        }
        return super.searchMangaRequest(page, query, filters)
    }

    override val seriesTitleSelector = ".lrs-title"
    override val seriesThumbnailSelector = "img.lrs-cover"
    override val seriesDescriptionSelector = ".lrs-syn-wrap"
    override val seriesStatusSelector = ".lrs-infotable tr:contains(Status) td:last-child"
    override val seriesGenreSelector = ".lrs-genre"

    override fun chapterListSelector() = "#lrs-native-chapterlist li"
}
