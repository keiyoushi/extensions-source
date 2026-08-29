package eu.kanade.tachiyomi.extension.id.okyykomik

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import okhttp3.Request
import okhttp3.Response

@Source
abstract class OkyyKomik : ZeistManga() {

    override val supportsLatest = false
    override val mangaDetailsSelector = "#Blog1"
    override val mangaDetailsSelectorAuthor = "#extra-info > dl:nth-child(2) dd"
    override val mangaDetailsSelectorArtist = "#extra-info > dl:nth-child(3) dd"
    override val pageListSelector = "article div.separator"

    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)
    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)
}
