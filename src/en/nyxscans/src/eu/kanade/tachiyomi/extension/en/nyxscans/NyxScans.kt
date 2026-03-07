package eu.kanade.tachiyomi.extension.en.nyxscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response

class NyxScans :
    Iken(
        "Nyx Scans",
        "en",
        "https://nyxscans.com",
        "https://api.nyxscans.com",
    ) {

    override val usePopularMangaApi = true

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)
}
