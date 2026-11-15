package eu.kanade.tachiyomi.extension.fr.hentaizone

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HentaiZone : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.FRENCH)
    override val mangaSubString = "tous-les-mangas"
}
