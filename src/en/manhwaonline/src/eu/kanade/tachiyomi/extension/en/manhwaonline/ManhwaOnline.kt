package eu.kanade.tachiyomi.extension.en.manhwaonline

import eu.kanade.tachiyomi.multisrc.yiimanga.Yiimanga
import keiyoushi.annotation.Source

@Source
abstract class ManhwaOnline : Yiimanga() {
    override val mangaPath = "manga"

    override val popularPath = "webtoons"
}
