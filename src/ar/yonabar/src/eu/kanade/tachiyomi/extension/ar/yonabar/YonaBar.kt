package eu.kanade.tachiyomi.extension.ar.yonabar

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Response
import org.jsoup.nodes.Document

class YonaBar :
    Madara(
        "Yona Bar",
        "https://yonaber.com",
        "ar",
    ) {

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val mangaSubString = "yaoi"

    // The next page has an error; it’s a site issue
    override fun popularMangaParse(response: Response): MangasPage = super.popularMangaParse(response).copy(hasNextPage = false)

    override fun latestUpdatesParse(response: Response): MangasPage = super.latestUpdatesParse(response).copy(hasNextPage = false)

    override fun pageListParse(document: Document): List<Page> = super.pageListParse(document)
        .map { page ->
            page.apply {
                imageUrl = imageUrl!!.replaceFirst("medium1", "medium1x")
            }
        }
}
