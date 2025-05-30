package eu.kanade.tachiyomi.extension.tr.anisamanga

import eu.kanade.tachiyomi.multisrc.etoshore.Etoshore
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.io.IOException

class AnisaManga : Etoshore(
    "Anisa Manga",
    "https://anisamanga.net",
    "tr",
) {
    // Migrate from Madara to Etoshore
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun pageListParse(document: Document): List<Page> {
        verifyLoginRequired(document)
        return super.pageListParse(document)
    }

    private fun verifyLoginRequired(document: Document) {
        val alert = document.selectFirst("h1")?.text() ?: return
        if (alert.contains("İçerik Kısıtlaması", ignoreCase = true)) {
            throw IOException("Web görünümünde oturum açın")
        }
    }
}
