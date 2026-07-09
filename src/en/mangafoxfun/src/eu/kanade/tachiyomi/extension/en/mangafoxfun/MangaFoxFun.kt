package eu.kanade.tachiyomi.extension.en.mangafoxfun

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub
import keiyoushi.annotation.Source

@Source
abstract class MangaFoxFun : MangaHub() {
    override val mangaSource = "mf01"
}
