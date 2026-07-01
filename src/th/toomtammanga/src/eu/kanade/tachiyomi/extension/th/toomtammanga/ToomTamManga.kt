package eu.kanade.tachiyomi.extension.th.toomtammanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class ToomTamManga : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th"))
    override val seriesAuthorSelector = ".imptdt:contains(ผู้เขียน) i"
    override val seriesArtistSelector = ".imptdt:contains(ศิลปิน) i"
    override val seriesTypeSelector = ".imptdt:contains(พิมพ์) a"
}
