package eu.kanade.tachiyomi.extension.en.hentai20

import eu.kanade.tachiyomi.multisrc.yiimanga.Yiimanga
import keiyoushi.annotation.Source

@Source
abstract class Hentai20 : Yiimanga() {
    override val mangaPath = "manga"

    override val popularPath = "manga-lists"

    override val supportsFilterFetching = true
}
