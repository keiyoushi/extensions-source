package eu.kanade.tachiyomi.extension.en.vyvymangaorg

import eu.kanade.tachiyomi.multisrc.madara.Madara

class VyvyMangaOrg : Madara(
    name = "VyvyManga.org",
    baseUrl = "https://vyvymanga.org",
    lang = "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
