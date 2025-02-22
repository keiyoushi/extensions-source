package eu.kanade.tachiyomi.extension.ja.mangakoinu
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig

class MangaKoinu : MCCMS(
    name = "Manga Koinu",
    baseUrl = "https://www.mangakoinu.com",
    lang = "ja",
    config = MCCMSConfig(lazyLoadImageAttr = "src"),
)
