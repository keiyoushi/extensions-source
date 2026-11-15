package eu.kanade.tachiyomi.extension.ca.fansubscathentai

import eu.kanade.tachiyomi.multisrc.fansubscat.FansubsCat
import keiyoushi.annotation.Source

@Source
abstract class HentaiCat : FansubsCat() {

    override val isHentaiSite = true
}
