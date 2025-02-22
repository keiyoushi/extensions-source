package eu.kanade.tachiyomi.extension.pt.aurorascan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan

class AuroraScan : PeachScan("Aurora Scan", "https://aurorascan.net", "pt-BR")
