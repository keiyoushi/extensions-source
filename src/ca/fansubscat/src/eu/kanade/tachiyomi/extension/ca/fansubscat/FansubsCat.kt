package eu.kanade.tachiyomi.extension.ca.fansubscat

import eu.kanade.tachiyomi.multisrc.fansubscat.FansubsCat
import keiyoushi.annotation.Source

@Source
abstract class FansubsCat : FansubsCat() {

    override val isHentaiSite = false
}
