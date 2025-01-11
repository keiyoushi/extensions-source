package eu.kanade.tachiyomi.extension.en.holymanga

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader

class HolyManga : FMReader(
    "HolyManga",
    "https://w34.holymanga.net",
    "en",
) {
    override val versionId = 2
}
