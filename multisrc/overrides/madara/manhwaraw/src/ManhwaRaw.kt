package eu.kanade.tachiyomi.extension.ko.manhwaraw

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhwaRaw : Madara("ManhwaRaw", "https://manhwaraw.com", "ko") {

    override val mangaSubString = "manhwa-raw"

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
