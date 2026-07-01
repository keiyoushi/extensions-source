package eu.kanade.tachiyomi.extension.id.omicaso

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class Omicaso : MangaThemesia() {
    override val mangaUrlDirectory = "/comik"
}
