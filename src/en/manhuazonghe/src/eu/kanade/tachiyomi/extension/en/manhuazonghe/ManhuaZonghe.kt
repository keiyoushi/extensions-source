package eu.kanade.tachiyomi.extension.en.manhuazonghe

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhuaZonghe : Madara("Manhua Zonghe", "https://www.manhuazonghe.com", "en") {
    override val useNewChapterEndpoint = false
    override val filterNonMangaItems = false
    override val mangaSubString = "manhua"
}
