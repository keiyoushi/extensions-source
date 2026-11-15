package eu.kanade.tachiyomi.extension.es.bloomscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Bloomscans : MangaThemesia() {
    override val mangaUrlDirectory = "/series"

    override fun searchMangaUrl(page: Int, query: String) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("page")
        .addPathSegment(page.toString())
        .addQueryParameter("s", query)
        .addPathSegment("")

    override val seriesTitleSelector = ".lrs-title"
    override val seriesThumbnailSelector = "img.lrs-cover"
    override val seriesDescriptionSelector = ".lrs-syn-wrap"
    override val seriesStatusSelector = ".lrs-infotable tr:contains(Status) td:last-child"
    override val seriesGenreSelector = ".lrs-genre"

    override fun chapterListSelector() = "#lrs-native-chapterlist li"
}
