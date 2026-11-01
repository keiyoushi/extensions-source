package eu.kanade.tachiyomi.extension.en.hentai20lol

import eu.kanade.tachiyomi.multisrc.yiimanga.Yiimanga
import keiyoushi.annotation.Source

@Source
abstract class Hentai20Lol : Yiimanga() {
    override val mangaPath = "manga"

    override val popularPath = "manga-lists"

    override val supportsFilterFetching = true
}
