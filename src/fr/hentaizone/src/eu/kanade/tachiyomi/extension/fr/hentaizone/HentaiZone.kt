package eu.kanade.tachiyomi.extension.fr.hentaizone

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class HentaiZone : Madara() {
    override val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.FRENCH)
    override val mangaSubString = "tous-les-mangas"
}
