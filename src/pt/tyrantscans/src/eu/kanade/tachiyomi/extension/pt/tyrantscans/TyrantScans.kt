package eu.kanade.tachiyomi.extension.pt.tyrantscans

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Request
import okhttp3.Response

class TyrantScans : ZeistManga("Tyrant Scans", "https://www.tyrantscans.com", "pt-BR") {

    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)
}
