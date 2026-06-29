package eu.kanade.tachiyomi.extension.ar.despairmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request

class DespairManga :
    MangaThemesia(
        "Despair Manga",
        "https://despair-manga.net",
        "ar",
    ) {

    override fun imageRequest(page: Page): Request {
        page.imageUrl = page.imageUrl!!.let {
            if (it.startsWith("http")) it else baseUrl + it
        }
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }
}
