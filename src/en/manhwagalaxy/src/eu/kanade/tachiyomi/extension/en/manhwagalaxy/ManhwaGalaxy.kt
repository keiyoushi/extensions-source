package eu.kanade.tachiyomi.extension.en.manhwagalaxy

import eu.kanade.tachiyomi.multisrc.yiimanga.Yiimanga
import keiyoushi.annotation.Source

@Source
abstract class ManhwaGalaxy : Yiimanga() {
    override val mangaPath = "manhwa"

    override val searchParam = "q"
}
