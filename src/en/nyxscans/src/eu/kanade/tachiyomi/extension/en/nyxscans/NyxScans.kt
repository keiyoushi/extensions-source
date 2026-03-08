package eu.kanade.tachiyomi.extension.en.nyxscans

import eu.kanade.tachiyomi.multisrc.iken.Iken

class NyxScans :
    Iken(
        "Nyx Scans",
        "en",
        "https://nyxscans.com",
        "https://api.nyxscans.com",
    ) {
    override val usePopularMangaApi = true
}
