package eu.kanade.tachiyomi.extension.id.manhwaindo

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaIndo : MangaThemesia(
    "Manhwa Indo",
    "https://manhwaindo.id",
    "id",
    "/series",
    SimpleDateFormat("MMMM dd, yyyy", Locale.US),
) {
    override val seriesTitleSelector = ".ts-breadcrumb li:last-child span"

    override val hasProjectPage = true
}
