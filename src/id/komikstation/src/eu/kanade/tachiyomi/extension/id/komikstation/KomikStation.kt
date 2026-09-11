package eu.kanade.tachiyomi.extension.id.komikstation

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

@Source
abstract class KomikStation : MangaThemesia() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(4)

    override val projectPageString = "/project-list"

    override val hasProjectPage = true

    override val pageSelector = "div#readerarea img:not(noscript img)"

    override fun searchMangaFromElement(element: Element): SManga = super.searchMangaFromElement(element).apply {
        element.selectFirst("img.ts-post-image")?.imgAttr()?.let { thumbnail_url = it }
    }
}
