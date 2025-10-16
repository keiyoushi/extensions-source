package eu.kanade.tachiyomi.extension.en.aurorascans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET

class AuroraScans : Iken(
    "Aurora Scans",
    "en",
    "https://aurorascans.com",
    "https://api.aurorascans.com",
) {
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)
}
