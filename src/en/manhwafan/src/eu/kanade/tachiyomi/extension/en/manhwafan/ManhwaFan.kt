package eu.kanade.tachiyomi.extension.en.manhwafan

import eu.kanade.tachiyomi.multisrc.zinmangaart.ZinMangaArt
import keiyoushi.annotation.Source

@Source
abstract class ManhwaFan : ZinMangaArt() {
    override val searchPath = "/home"
}
