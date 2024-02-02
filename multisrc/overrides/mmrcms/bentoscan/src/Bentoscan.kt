package eu.kanade.tachiyomi.extension.fr.bentoscan

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request

class Bentoscan : MMRCMS(
    "Bentoscan",
    "https://bentoscan.com",
    "fr",
    supportsAdvancedSearch = false,
    chapterNamePrefix = "Scan ",
) {
    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "https://scansmangas.me/")
            .set("Accept", "image/avif,image/webp,*/*")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }
}
