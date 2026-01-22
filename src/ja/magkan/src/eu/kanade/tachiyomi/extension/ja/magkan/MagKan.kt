package eu.kanade.tachiyomi.extension.ja.magkan

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Request
import okhttp3.Response

class MagKan : ComiciViewer(
    "MagKan",
    "https://kansai.mag-garden.co.jp",
    "ja",
) {
    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("読み切り", "/category/manga?type=読み切り"),
    )
}
