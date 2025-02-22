package eu.kanade.tachiyomi.extension.ar.detectiveconanar
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class DetectiveConanAr : Madara("شبكة كونان العربية", "https://manga.detectiveconanar.com", "ar")
