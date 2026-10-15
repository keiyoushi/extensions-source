package eu.kanade.tachiyomi.extension.es.lunapieces

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class LunaPieces : MangaThemesia() {
    override val mangaUrlDirectory = "/doujinshi"
    override val datePattern = "d MMMM, yyyy"
}
