package eu.kanade.tachiyomi.extension.id.sasangeyou

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Sasangeyou :
    MangaThemesia(
        "Sasangeyou",
        "https://sasangeyou.net",
        "id",
        "/manga",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id")),
    ) {

    override val hasProjectPage = false
}
