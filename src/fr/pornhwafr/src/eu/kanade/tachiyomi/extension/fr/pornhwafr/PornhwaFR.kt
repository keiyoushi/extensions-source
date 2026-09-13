package eu.kanade.tachiyomi.extension.fr.pornhwafr

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class PornhwaFR : MangaThemesia() {
    override val mangaUrlDirectory = "/catalogue"
    override val altNamePrefix = "Nom alternatif : "
}
