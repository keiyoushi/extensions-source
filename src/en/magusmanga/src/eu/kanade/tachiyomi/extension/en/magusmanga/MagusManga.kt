package eu.kanade.tachiyomi.extension.en.magusmanga

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

class MagusManga : Iken(
    "Magus Manga",
    "en",
    "https://magustoon.org",
    "https://api.magustoon.org",
) {
    // Moved from Keyoapp to Iken
    override val versionId = 3

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1)
        .build()

    override val sortPagesByFilename = true

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val entries = response.asJsoup().select(".splide__track li > a").mapNotNull {
            titleCache[it.absUrl("href").substringAfter("series/")]?.toSManga()
        }.distinctBy(SManga::url)
        return MangasPage(entries, false)
    }
}
