package eu.kanade.tachiyomi.extension.id.siimanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request

class Siikomik : MangaThemesia(
    "Siikomik",
    "https://siikomik.art",
    "id",
) {
    override val versionId = 2

    override val hasProjectPage = true

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .removeAll("Referer")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }
}
