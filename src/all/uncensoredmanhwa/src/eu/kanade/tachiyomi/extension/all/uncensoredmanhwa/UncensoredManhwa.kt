package eu.kanade.tachiyomi.extension.all.uncensoredmanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class UncensoredManhwa : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es"))
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override fun popularMangaParse(response: Response): MangasPage {
        val page = super.popularMangaParse(response)
        return if (lang == "en") MangasPage(page.mangas.filterNot { it.title.endsWith(" Raw") }, page.hasNextPage) else page
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val page = super.searchMangaParse(response)
        return if (lang == "en") MangasPage(page.mangas.filterNot { it.title.endsWith(" Raw") }, page.hasNextPage) else page
    }
}
