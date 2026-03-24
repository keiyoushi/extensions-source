package eu.kanade.tachiyomi.extension.id.kumopoi

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class KumoPoi :
    MangaThemesia(
        "KumoPoi",
        "https://kumopoi.org",
        "id",
        "/manga",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id")),
    ) {

    override val hasProjectPage = true
}
