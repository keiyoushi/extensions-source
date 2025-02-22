package eu.kanade.tachiyomi.extension.en.mangaonlinefun
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub

class MangaOnlineFun : MangaHub("MangaOnline.fun", "https://mangaonline.fun", "en", "m02")
