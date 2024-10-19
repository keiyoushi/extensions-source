package eu.kanade.tachiyomi.extension.en.vyvymanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class VyvyManga : Madara(
    name = "VyvyManga",
    baseUrl = "https://vyvymanga.org",
    lang = "en",
) {
    override val versionId = 2

    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
