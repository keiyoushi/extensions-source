package eu.kanade.tachiyomi.extension.en.infernalvoidscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class InfernalVoidScans : MangaThemesia("Infernal Void Scans", "https://void-scans.com", "en") {
    override val pageSelector = "div#readerarea > p > img"
}
