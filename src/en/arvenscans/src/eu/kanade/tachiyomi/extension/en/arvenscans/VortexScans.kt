package eu.kanade.tachiyomi.extension.en.arvenscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET

class VortexScans : Iken(
    "Vortex Scans",
    "en",
    "https://vortexscans.org",
    "https://api.vortexscans.org",
) {
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)
}
