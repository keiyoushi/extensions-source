package eu.kanade.tachiyomi.extension.en.cosmicscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class CosmicScans : MangaThemesia("Cosmic Scans", "https://cosmic-scans.com", "en") {
    override val pageSelector = "div#readerarea img[data-src]"
}
