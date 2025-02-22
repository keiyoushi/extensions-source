package eu.kanade.tachiyomi.extension.en.onemangaco
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub

class OneMangaCo : MangaHub("1Manga.co", "https://1manga.co", "en", "mn03")
