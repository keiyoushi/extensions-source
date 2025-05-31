package eu.kanade.tachiyomi.extension.id.komikdewasa

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class KomikDewasa : MangaThemesia(
    "Komik Dewasak",
    "https://komikdewasa.mom",
    "id",
    mangaUrlDirectory = "/komik",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
) {
    override val hasProjectPage = true
}
