package eu.kanade.tachiyomi.extension.fr.bentoscan

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Bentoscan : MMRCMS("Bentoscan", "https://bentoscan.com", "fr") {
    override val supportsAdvancedSearch = false

    override val chapterNamePrefix = "Scan "

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "https://scansmangas.me/")
            .set("Accept", "image/avif,image/webp,*/*")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }
}
