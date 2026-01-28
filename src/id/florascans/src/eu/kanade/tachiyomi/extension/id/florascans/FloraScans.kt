package eu.kanade.tachiyomi.extension.id.florascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class FloraScans : MangaThemesia(
    "FloraScans",
    "https://florascans.net",
    "id",
    "/manga",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id")),
) {

    override val hasProjectPage = true
}
