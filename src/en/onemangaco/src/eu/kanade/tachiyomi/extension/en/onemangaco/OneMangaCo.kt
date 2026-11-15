package eu.kanade.tachiyomi.extension.en.onemangaco

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub
import keiyoushi.annotation.Source

@Source
abstract class OneMangaCo : MangaHub() {
    override val mangaSource = "mn03"
}
