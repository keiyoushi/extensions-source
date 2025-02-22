package eu.kanade.tachiyomi.extension.pt.dangoscan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan

class DangoScan : PeachScan("Dango Scan", "https://dangoscan.com.br", "pt-BR")
