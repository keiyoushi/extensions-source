package eu.kanade.tachiyomi.extension.en.beehentai
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madtheme.MadTheme

class BeeHentai : MadTheme("BeeHentai", "https://beehentai.com", "en")
