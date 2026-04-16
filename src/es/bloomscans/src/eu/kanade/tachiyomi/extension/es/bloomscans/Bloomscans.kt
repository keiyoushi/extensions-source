package eu.kanade.tachiyomi.extension.es.bloomscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
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
    override val seriesTitleSelector = ".lrs-title"
    override val seriesThumbnailSelector = "img.lrs-cover"
    override val seriesDescriptionSelector = ".lrs-syn-wrap"
    override val seriesStatusSelector = ".lrs-infotable tr:contains(Status) td:last-child"
    override val seriesGenreSelector = ".lrs-genre"

    override fun chapterListSelector() = "#lrs-native-chapterlist li"
}
