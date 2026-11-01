package eu.kanade.tachiyomi.extension.en.manhwa18tx

import eu.kanade.tachiyomi.multisrc.yiimanga.Yiimanga
import keiyoushi.annotation.Source

@Source
abstract class Manhwa18Tx : Yiimanga() {
    override val mangaPath = "manhwa"

    override val searchParam = "q"
}
