package eu.kanade.tachiyomi.extension.en.mangabuddy
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madtheme.MadTheme

class MangaBuddy : MadTheme("MangaBuddy", "https://mangabuddy.com", "en")
