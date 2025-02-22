package eu.kanade.tachiyomi.extension.pt.nazarickscan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan

class NazarickScan : PeachScan("Nazarick Scan", "https://nazarickscan.com.br", "pt-BR")
