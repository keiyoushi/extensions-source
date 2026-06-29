package eu.kanade.tachiyomi.extension.ar.xsanomanga

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import okhttp3.Response

class XSanoManga :
    ZeistManga(
        "XSano Manga",
        "https://www.xsano-manga.com",
        "ar",
    ) {

    // Missing popular
    override val supportsLatest = false
    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)
    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)

    override val mangaDetailsSelector = "main"

    override val mangaDetailsSelectorGenres = "dl a[rel=tag]"

    override val mangaDetailsSelectorInfo = "#extra-info dl"
    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"

    override val pageListSelector = "#reader div.separator"
}
