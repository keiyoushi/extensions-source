package eu.kanade.tachiyomi.extension.all.manhwa18cc

import eu.kanade.tachiyomi.multisrc.yiimadara.YiiMadara
import keiyoushi.annotation.Source

@Source
abstract class Manhwa18Cc : YiiMadara() {
    override val mangaPath = "webtoon"
    override val rawPath = "raw"

    override val directoryPath get() = if (lang == "ko") rawPath else "webtoons"
}
