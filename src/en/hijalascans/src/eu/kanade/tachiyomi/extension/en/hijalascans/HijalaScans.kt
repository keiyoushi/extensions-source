package eu.kanade.tachiyomi.extension.en.hijalascans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import okhttp3.Response

class HijalaScans :
    Iken(
        "Hijala Scans",
        "en",
        "https://en-hijala.com",
        "https://api.en-hijala.com",
    ) {

    override val usePopularMangaApi = true

    override fun popularMangaParse(response: Response) = searchMangaParse(response)
}
