package eu.kanade.tachiyomi.extension.ar.ozulscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import java.text.SimpleDateFormat
import java.util.Locale

class KingOfManga : MangaThemesiaAlt(
    "King Of Manga",
    "https://king-ofmanga.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    // Ozul Scans -> King of Manga
    override val id = 3453769904666687440
}
