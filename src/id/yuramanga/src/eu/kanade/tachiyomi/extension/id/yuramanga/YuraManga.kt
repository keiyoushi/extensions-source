package eu.kanade.tachiyomi.extension.id.yuramanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class YuraManga : Madara("YuraManga", "https://yuramanga.my.id", "id") {
    // Moved from Makaru to Madara
    override val versionId = 2

    // If .list-chapter is empty the link is 404
    override fun popularMangaSelector() = "div.page-item-detail:not(:has(a[href*='bilibilicomics.com']))$mangaEntrySelector:has(.chapter-item)"
}
