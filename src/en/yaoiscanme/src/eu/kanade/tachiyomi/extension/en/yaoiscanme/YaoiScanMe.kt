package eu.kanade.tachiyomi.extension.en.yaoiscanme

import eu.kanade.tachiyomi.multisrc.yiimanga.Yiimanga
import keiyoushi.annotation.Source

@Source
abstract class YaoiScanMe : Yiimanga() {
    override val mangaPath = "read"

    override val searchParam = "q"
}
