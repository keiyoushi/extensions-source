package eu.kanade.tachiyomi.extension.ar.azora

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response

class Azora :
    Iken(
        "Azora",
        "ar",
        "https://azoramoon.com",
        "https://api.azoramoon.com",
    ) {

    override val versionId = 2

    override val usePopularMangaApi = true

    override fun popularMangaUrl(page: Int) = super.popularMangaUrl(page)
        .addQueryParameter("orderDirection", "desc")

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)
}
