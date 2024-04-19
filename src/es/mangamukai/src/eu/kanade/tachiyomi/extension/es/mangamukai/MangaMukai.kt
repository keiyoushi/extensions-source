package eu.kanade.tachiyomi.extension.es.mangamukai

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MangaMukai : MangaThemesia(
    "MangaMukai",
    "https://mangamukai.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val id: Long = 711368877221654433
}
