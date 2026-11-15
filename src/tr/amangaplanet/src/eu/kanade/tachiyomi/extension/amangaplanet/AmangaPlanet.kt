package eu.kanade.tachiyomi.extension.tr.amangaplanet

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class AmangaPlanet : MangaThemesia() {
    override val datePattern = "dd/MM/yyyy"
}
