package eu.kanade.tachiyomi.extension.en.skymangaxyz

import eu.kanade.tachiyomi.multisrc.madara.Madara

class SkyMangaXyz : Madara("SkyManga.xyz", "https://skymanga.xyz", "en") {
    override val useNewChapterEndpoint = true
}
