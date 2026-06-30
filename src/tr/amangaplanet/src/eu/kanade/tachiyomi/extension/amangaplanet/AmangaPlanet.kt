package eu.kanade.tachiyomi.extension.tr.amangaplanet

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class AmangaPlanet : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
}
