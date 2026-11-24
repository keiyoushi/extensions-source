package eu.kanade.tachiyomi.extension.en.harimangacouk

import eu.kanade.tachiyomi.multisrc.zinmangaart.ZinMangaArt
import keiyoushi.annotation.Source

@Source
abstract class HariMangaCoUk : ZinMangaArt() {
    override val searchPath = "/home"
}
