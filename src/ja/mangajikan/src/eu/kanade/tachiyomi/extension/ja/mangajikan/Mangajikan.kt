package eu.kanade.tachiyomi.extension.ja.mangajikan

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig

class Mangajikan : MCCMS(
    name = "漫画時間",
    baseUrl = "https://www.mangajikan.com",
    lang = "ja",
    config = MCCMSConfig(lazyLoadImageAttr = "src"),
)
