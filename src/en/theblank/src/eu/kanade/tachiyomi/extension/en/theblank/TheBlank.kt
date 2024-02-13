package eu.kanade.tachiyomi.extension.en.theblank

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class TheBlank : Madara(
    "The Blank Scanlation",
    "https://theblank.net",
    "en",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US),
) {

    override val client = super.client.newBuilder()
        .rateLimit(1)
        .build()

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
