package eu.kanade.tachiyomi.extension.en.mangaonlinefun

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub
import keiyoushi.annotation.Source

@Source
abstract class MangaOnlineFun : MangaHub() {
    override val mangaSource = "m02"
}
