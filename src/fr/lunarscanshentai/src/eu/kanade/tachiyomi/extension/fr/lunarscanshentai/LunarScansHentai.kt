package eu.kanade.tachiyomi.extension.fr.lunarscanshentai

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class LunarScansHentai : MangaThemesia(
    "Lunar Scans Hentai",
    "https://hentai.lunarscans.fr",
    "fr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH),
) {
    override val supportsLatest = false
}
