package eu.kanade.tachiyomi.extension.th.goddoujin

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class GodDoujin : MangaThemesia(
    "God-Doujin",
    "https://god-doujin.com",
    "th",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")),
) {
    override val seriesTypeSelector = ".imptdt:contains(ประเภท) a"
}
