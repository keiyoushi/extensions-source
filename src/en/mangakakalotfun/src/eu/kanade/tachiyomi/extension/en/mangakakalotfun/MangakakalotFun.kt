package eu.kanade.tachiyomi.extension.en.mangakakalotfun

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub
import keiyoushi.annotation.Source

@Source
abstract class MangakakalotFun : MangaHub() {
    override val mangaSource = "mn01"
}
