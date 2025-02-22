package eu.kanade.tachiyomi.extension.en.mangamaniacs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaManiacs : Madara("MangaManiacs", "https://mangamaniacs.org", "en")
