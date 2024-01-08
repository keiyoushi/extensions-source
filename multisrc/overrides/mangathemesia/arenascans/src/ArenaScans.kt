package eu.kanade.tachiyomi.extension.en.arenascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class ArenaScans : MangaThemesia("Arena Scans", "https://arenascans.net", "en") {
    override val pageSelector = "div#readerarea img[data-src]"
}
