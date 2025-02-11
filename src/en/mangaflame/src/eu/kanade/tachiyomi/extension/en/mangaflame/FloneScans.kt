package eu.kanade.tachiyomi.extension.en.mangaflame

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class FloneScans : MangaThemesia(
    "Flone Scans",
    "https://sweetmanhwa.online",
    "en",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH),
) {
    override val id = 1501237443119573205
}
