package eu.kanade.tachiyomi.extension.es.lkscanlation

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class LKScanlation : Madara(
    "Last Knight Translation",
    "https://lkscanlation.com",
    "es",
    SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1)
        .build()

    override val useNewChapterEndpoint = true

    override val mangaSubString = "manhwa"

    override val popularMangaUrlSelector = "div.post-title a:not([target='_self'])"

    override val mangaDetailsSelectorAuthor = "div.manga-authors > a"
    override val mangaDetailsSelectorDescription = "div.manga-summary"
}
