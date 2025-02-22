package eu.kanade.tachiyomi.extension.en.sleepytranslations
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class SleepyTranslations : Madara("Sleepy Translations", "https://sleepytranslations.com", "en")
