package eu.kanade.tachiyomi.extension.id.mangatale

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET

class Ikiru : MangaThemesia(
    "Ikiru",
    "id",
    "https://id.ikiru.wtf",
    "https://api.id.ikiru.wtf",
) {
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)
}
