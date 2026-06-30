package eu.kanade.tachiyomi.extension.ar.goonscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class GoonScans : MangaThemesia() {
    override val mangaUrlDirectory = "/title"
}
