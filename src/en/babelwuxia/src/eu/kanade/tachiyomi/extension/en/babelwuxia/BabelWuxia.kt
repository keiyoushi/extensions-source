package eu.kanade.tachiyomi.extension.en.babelwuxia

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response

class BabelWuxia : Madara("Babel Wuxia", "https://babelwuxia.com", "en") {

    // moved from MangaThemesia
    override val versionId = 2
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String {
        return if (page > 1) {
            "page/$page/"
        } else {
            ""
        }
    }

    override fun popularMangaParse(response: Response) =
        super.popularMangaParse(response).fixNextPage()

    override fun latestUpdatesParse(response: Response) =
        super.latestUpdatesParse(response).fixNextPage()

    override fun searchMangaParse(response: Response) =
        super.searchMangaParse(response).fixNextPage()

    private fun MangasPage.fixNextPage(): MangasPage {
        return if (mangas.size < 12) {
            MangasPage(mangas, false)
        } else {
            this
        }
    }
}
