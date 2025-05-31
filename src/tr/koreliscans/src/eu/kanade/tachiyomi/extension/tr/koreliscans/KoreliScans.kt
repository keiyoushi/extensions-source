package eu.kanade.tachiyomi.extension.tr.koreliscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class KoreliScans : Madara(
    "Koreli Scans",
    "https://koreliscans.com",
    "tr",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override fun searchMangaParse(response: Response): MangasPage {
        val mangasPage = super.searchMangaParse(response)
        val filteredMangas = mangasPage.mangas.filterNot { it.title.endsWith(" Novel") }
        return MangasPage(filteredMangas, mangasPage.hasNextPage)
    }
}
