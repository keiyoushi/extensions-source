package eu.kanade.tachiyomi.extension.en.mangafoxfun
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub

class MangaFoxFun : MangaHub("MangaFox.fun", "https://mangafox.fun", "en", "mf01")
