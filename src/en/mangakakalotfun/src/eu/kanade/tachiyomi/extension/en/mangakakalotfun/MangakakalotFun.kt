package eu.kanade.tachiyomi.extension.en.mangakakalotfun
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub

class MangakakalotFun : MangaHub("Mangakakalot.fun", "https://mangakakalot.fun", "en", "mn01")
