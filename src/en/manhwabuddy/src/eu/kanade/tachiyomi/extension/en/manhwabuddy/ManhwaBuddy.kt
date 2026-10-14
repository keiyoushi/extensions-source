package eu.kanade.tachiyomi.extension.en.manhwabuddy

import eu.kanade.tachiyomi.multisrc.yiimanga.Yiimanga
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup

@Source
abstract class ManhwaBuddy : Yiimanga() {
    override val mangaPath = "manhwa"

    override val supportsLatest = true

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get(baseUrl).asJsoup().select(".item-move").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(".name a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }
}
