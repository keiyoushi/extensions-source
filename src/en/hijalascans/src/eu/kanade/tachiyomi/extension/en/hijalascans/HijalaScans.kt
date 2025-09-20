package eu.kanade.tachiyomi.extension.en.hijalascans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class HijalaScans : Iken(
    "Hijala Scans",
    "en",
    "https://en-hijala.com",
    "https://api.en-hijala.com",
) {
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val entries = response.asJsoup().select(".splide__track li > a").mapNotNull {
            titleCache[it.absUrl("href").substringAfter("series/")]?.toSManga()
        }.distinctBy(SManga::url)
        return MangasPage(entries, false)
    }
}
