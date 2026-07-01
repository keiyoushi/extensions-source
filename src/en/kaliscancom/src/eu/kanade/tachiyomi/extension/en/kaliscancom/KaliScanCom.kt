package eu.kanade.tachiyomi.extension.en.kaliscancom

import eu.kanade.tachiyomi.multisrc.madtheme.MadTheme
import keiyoushi.annotation.Source

@Source
abstract class KaliScanCom : MadTheme() {

    override val useLegacyApi = true
}
