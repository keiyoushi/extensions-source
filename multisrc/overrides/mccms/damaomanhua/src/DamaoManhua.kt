package eu.kanade.tachiyomi.extension.zh.damaomanhua

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

class DamaoManhua : MCCMS(
    "大猫漫画",
    "https://www.hanman.cyou/index.php",
    "zh",
    MCCMSConfig(useMobilePageList = true),
) {
    // Details and chapter pages are broken
    override fun getMangaUrl(manga: SManga) = baseUrl
    override fun getChapterUrl(chapter: SChapter) = baseUrl
}
