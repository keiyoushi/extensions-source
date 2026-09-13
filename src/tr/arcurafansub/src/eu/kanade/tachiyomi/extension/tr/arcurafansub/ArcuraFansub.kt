package eu.kanade.tachiyomi.extension.tr.arcurafansub

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class ArcuraFansub : MangaThemesia() {
    override val mangaUrlDirectory = "/seri"
}
