package eu.kanade.tachiyomi.extension.ja.mangakoma

import eu.kanade.tachiyomi.multisrc.liliana.Liliana
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class MangaKoma : Liliana("Manga Koma", "https://mangakoma01.net", "ja") {
    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
            removeAll("Referer")
        }.build()
        return GET(page.imageUrl!!, imgHeaders)
    }
}
