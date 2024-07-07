package eu.kanade.tachiyomi.extension.id.siimanga2

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request

class Siimanga : MangaThemesia(
    "Siimanga",
    "https://siimanga.cyou",
    "id",
) {
    override val hasProjectPage = true

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .removeAll("Referer")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }
}
