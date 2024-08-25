package eu.kanade.tachiyomi.extension.en.hunlightscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Response

class HunlightScans : Madara(
    "Hunlight Scans",
    "https://hunlight.com",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val filterNonMangaItems = false

    override fun chapterListParse(response: Response) = super.chapterListParse(response).reversed()
}
