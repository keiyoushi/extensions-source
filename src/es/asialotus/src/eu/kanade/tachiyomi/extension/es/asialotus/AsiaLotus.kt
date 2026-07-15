package eu.kanade.tachiyomi.extension.es.asialotus

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit

@Source
abstract class AsiaLotus : MangaThemesia() {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
