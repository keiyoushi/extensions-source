package eu.kanade.tachiyomi.extension.en.manhwaus

import eu.kanade.tachiyomi.multisrc.yiimadara.YiiMadara
import keiyoushi.annotation.Source

@Source
abstract class ManhwaUS : YiiMadara() {
    override val mangaPath = "webtoon"
    override val directoryPath = "webtoons"
    override val pageSegment = true
    override val trailingSlash = true
    override val searchParam = "s"
    override val rawPath = "raw"
}
