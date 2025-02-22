package eu.kanade.tachiyomi.extension.pt.norterosescan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class NorteRoseScan : Madara("Norte Rose Scan", "https://norterose.xyz", "pt-BR")
