package eu.kanade.tachiyomi.extension.id.futari

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Futari : MangaThemesia(
    "Futari",
    "https://futari.info",
    "id",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id")),
) {
    override val hasProjectPage = true
}
