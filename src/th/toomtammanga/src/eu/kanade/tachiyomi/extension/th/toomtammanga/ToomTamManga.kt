package eu.kanade.tachiyomi.extension.th.toomtammanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class ToomTamManga : MangaThemesia(
    "ToomTam-Manga",
    "https://toomtam-manga.com",
    "th",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")),
) {
    override val seriesAuthorSelector = ".imptdt:contains(ผู้เขียน) i"
    override val seriesArtistSelector = ".imptdt:contains(ศิลปิน) i"
    override val seriesTypeSelector = ".imptdt:contains(พิมพ์) a"
}
