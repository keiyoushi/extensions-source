package eu.kanade.tachiyomi.extension.ja.mangakoinu

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig

class MangaKoinu : MCCMS(
    name = "Manga Koinu",
    baseUrl = "https://www.mangakoinu.com",
    lang = "ja",
    config = MCCMSConfig(lazyLoadImageAttr = "src"),
)
