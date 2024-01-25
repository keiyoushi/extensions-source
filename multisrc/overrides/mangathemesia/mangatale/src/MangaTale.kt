package eu.kanade.tachiyomi.extension.id.mangatale

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

class MangaTale : MangaThemesia("MangaTale", "https://mangatale.co", "id") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 5)
        .build()

    override val seriesTitleSelector = ".ts-breadcrumb li:last-child span"

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        thumbnail_url = document.selectFirst(seriesThumbnailSelector)?.imgAttr()
    }
}
