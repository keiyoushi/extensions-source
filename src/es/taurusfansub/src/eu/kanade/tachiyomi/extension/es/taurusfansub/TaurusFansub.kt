package eu.kanade.tachiyomi.extension.es.taurusfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TaurusFansub : Madara(
    "Taurus Fansub",
    "https://taurusmanga.com",
    "es",
    dateFormat = SimpleDateFormat("dd/MM/yyy", Locale.ROOT),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        manga.description = document.select("div.site-content div.summary_content p").text()
        manga.genre = document.select("div.site-content div.summary_content div.genres-content").joinToString { it.text() }
        manga.author = document.select("div.site-content div.summary_content div.tags-content").text()

        val stado = document.select("div.site-content div.summary_content div.manga-title div.post-content_item div.summary-content").first()?.text()
        manga.status = when (stado) {
            "En Curso" -> { SManga.ONGOING }
            "Completado" -> { SManga.COMPLETED }
            else -> { SManga.UNKNOWN }
        }
        manga.artist = document.select("div.site-content div.summary_content div.tags-content").text()

        return manga
    }
}
