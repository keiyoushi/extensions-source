package eu.kanade.tachiyomi.extension.id.sekaikomik

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class Sekaikomik : MangaThemesia(
    "Sekaikomik",
    "https://sekaikomik.bio",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
) {

    private val junkDescriptionPattern = """ Link Download : .*""".toRegex()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)

        // Remove junk from description
        manga.description = manga.description?.replace(junkDescriptionPattern, "")

        return manga
    }
}
