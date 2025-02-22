package eu.kanade.tachiyomi.extension.en.onemangainfo
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub

// Some chapters link to 1manga.co, hard to filter
class OneMangaInfo : MangaHub("OneManga.info", "https://onemanga.info", "en", "mh01")
