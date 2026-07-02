package eu.kanade.tachiyomi.extension.pt.monsure

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Monsure : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = true

    override val popularMangaUrlSelector = "${super.popularMangaUrlSelector}:not([href*=instagram])"
}
