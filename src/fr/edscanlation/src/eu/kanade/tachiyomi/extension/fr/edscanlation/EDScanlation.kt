package eu.kanade.tachiyomi.extension.fr.edscanlation
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class EDScanlation : Keyoapp("ED Scanlation", "https://edscanlation.fr", "fr")
