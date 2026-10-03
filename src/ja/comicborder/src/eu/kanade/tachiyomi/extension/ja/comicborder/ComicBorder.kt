package eu.kanade.tachiyomi.extension.ja.comicborder

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup

@Source
abstract class ComicBorder : GigaViewer() {
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get(baseUrl).asJsoup().select("section.top-series").map {
            SManga.create().apply {
                title = it.selectFirst("h3")!!.text()
                thumbnail_url = it.selectFirst(".top-key-image")?.absUrl("data-src")
                setSeriesUrl(it.selectFirst("li.first-episode a, li.this-episode a")!!.absUrl("href"), thumbnail_url)
            }
        }
        return MangasPage(mangas, false)
    }
}
