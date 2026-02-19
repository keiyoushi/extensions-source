package eu.kanade.tachiyomi.extension.pt.monsure

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class Monsure :
    Madara(
        "Monsure",
        "https://monsuresu.com",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
    ) {

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = true

    override val popularMangaUrlSelector = "${super.popularMangaUrlSelector}:not([href*=instagram])"
}
