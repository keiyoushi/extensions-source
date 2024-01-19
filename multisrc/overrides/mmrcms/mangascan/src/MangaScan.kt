package eu.kanade.tachiyomi.extension.fr.mangascan

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response

class MangaScan : MMRCMS("Manga-Scan", "https://mangascan-fr.com", "fr") {

    override fun mangaDetailsParse(response: Response): SManga {
        return super.mangaDetailsParse(response).apply {
            title = title.substringBefore("Chapitres en ligne").substringAfter("Scan").trim()
        }
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl)
            .set("Accept", "image/avif,image/webp,*/*")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }
}
