package eu.kanade.tachiyomi.extension.id.mangatale

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET

class Ikiru : Iken(
    "ikiru",
    "id",
    "https://id.ikiru.wtf",
    "https://api.id.ikiru.wtf",
) {
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)
}
