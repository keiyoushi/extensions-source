package eu.kanade.tachiyomi.extension.pt.baixarhentai

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class BaixarHentai : FoolSlide("Baixar Hentai", "https://leitura.baixarhentai.net", "pt-BR") {
    // Hardcode the id because the language wasn't specific.
    override val id = 8908032188831949972

    override val client = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1.title").text()
        thumbnail_url = getDetailsThumbnail(document, "div.title a")
    }

    // Always show adult content
    override val allowAdult = true

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}
}
