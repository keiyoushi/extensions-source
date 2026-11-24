package eu.kanade.tachiyomi.extension.en.harimangauscom

import eu.kanade.tachiyomi.multisrc.zinmangaart.ZinMangaArt
import keiyoushi.annotation.Source

@Source
abstract class HariMangaUsCom : ZinMangaArt() {
    override val searchPath = "/home"
}
