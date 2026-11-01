package eu.kanade.tachiyomi.extension.en.readmanga18

import eu.kanade.tachiyomi.multisrc.yiimadara.YiiMadara
import keiyoushi.annotation.Source

@Source
abstract class ReadManga18 : YiiMadara() {
    override val trailingSlash = true
    override val searchParam = "s"
    override val completedPath: String? = null
    override val rawPath = "raw"
}
