package eu.kanade.tachiyomi.extension.en.asmotoon

import eu.kanade.tachiyomi.multisrc.keyoapp.KeyoappV2
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.textOrNull
import org.jsoup.nodes.Document

@Source
abstract class Asmotoon : KeyoappV2() {
    override fun parseMangaDetails(document: Document, slug: String): SManga = super.parseMangaDetails(document, slug).apply {
        // "By Author, Artist" -> "Author", "Artist"
        // "By Author, Artist, Artist2" -> "Author", "Artist, Artist2"
        val byText = document.selectFirst("p.l2-by")?.textOrNull()?.removePrefix("By ")?.trim()
        if (byText != null) {
            author = byText.substringBefore(",").trim()
            val artistText = byText.substringAfter(",", "").trim()
            if (artistText.isNotEmpty()) {
                artist = artistText
            }
        }

        // "Completed series", falling back to "Manga · Action · Completed"
        val kickerText = (
            document.selectFirst("p.l2-kicker")?.textOrNull()
                ?: document.selectFirst("div.l2-meta span")?.textOrNull()
            )?.lowercase()

        if (kickerText != null) {
            status = when {
                "ongoing" in kickerText -> SManga.ONGOING
                "completed" in kickerText -> SManga.COMPLETED
                "dropped" in kickerText -> SManga.CANCELLED
                "paused" in kickerText || "hiatus" in kickerText -> SManga.ON_HIATUS
                else -> status
            }
        }
    }
}
