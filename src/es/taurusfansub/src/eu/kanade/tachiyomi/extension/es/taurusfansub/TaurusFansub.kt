package eu.kanade.tachiyomi.extension.es.taurusfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TaurusFansub : Madara(
    "Taurus Fansub",
    "https://lectortaurus.com",
    "es",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val popularMangaUrlSelectorImg = ".manga__thumb_item img"

    override val mangaDetailsSelectorTitle = "h1.post-title"
    override val mangaDetailsSelectorStatus = "div.manga-status span:last-child"
    override val mangaDetailsSelectorDescription = "div.summary__content p"

    override fun parseGenres(document: Document): List<Genre> {
        return document.select(".genres-filter .options a")
            .mapNotNull { element ->
                val id = element.absUrl("href").toHttpUrlOrNull()?.queryParameter("genre")
                val name = element.text()

                id?.takeIf { it.isNotEmpty() && name.isNotBlank() }
                    ?.let { Genre(name, it) }
            }
    }
}
