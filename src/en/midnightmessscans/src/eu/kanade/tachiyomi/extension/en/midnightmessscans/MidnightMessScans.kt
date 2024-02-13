package eu.kanade.tachiyomi.extension.en.midnightmessscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import java.util.Locale

class MidnightMessScans : Madara("Midnight Mess Scans", "https://midnightmess.org", "en") {

    override fun popularMangaSelector() = "div.page-item-detail:not(:has(a[href*='mangadex.org']))"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        with(document) {
            select("div.post-title h3").first()?.let {
                manga.title = it.ownText()
            }
            select("div.author-content").first()?.let {
                if (it.text().notUpdating()) manga.author = it.text()
            }
            select("div.artist-content").first()?.let {
                if (it.text().notUpdating()) manga.artist = it.text()
            }
            select("div.summary_content div.post-content").let {
                manga.description = it.select("div.manga-excerpt").text()
            }
            select("div.summary_image img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
            select("div.summary-content").last()?.let {
                manga.status = when (it.text()) {
                    // I don't know what's the corresponding for COMPLETED and LICENSED
                    // There's no support for "Canceled" or "On Hold"
                    "Completed", "Completo", "Concluído", "Concluido", "Terminé" -> SManga.COMPLETED
                    "OnGoing", "Продолжается", "Updating", "Em Lançamento", "Em andamento", "Em Andamento", "En cours", "Ativo", "Lançando" -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
            val genres = select("div.genres-content a")
                .map { element -> element.text().lowercase(Locale.ROOT) }
                .toMutableSet()

            // add tag(s) to genre
            select("div.tags-content a").forEach { element ->
                if (genres.contains(element.text()).not()) {
                    genres.add(element.text().lowercase(Locale.ROOT))
                }
            }

            // add manga/manhwa/manhua thinggy to genre
            document.select(seriesTypeSelector).firstOrNull()?.ownText()?.let {
                if (it.isEmpty().not() && it.notUpdating() && it != "-" && genres.contains(it).not()) {
                    genres.add(it.lowercase(Locale.ROOT))
                }
            }

            manga.genre = genres.toList().joinToString(", ") { it.capitalize(Locale.ROOT) }

            // add alternative name to manga description
            document.select(altNameSelector).firstOrNull()?.ownText()?.let {
                if (it.isBlank().not() && it.notUpdating()) {
                    manga.description = when {
                        manga.description.isNullOrBlank() -> altName + it
                        else -> manga.description + "\n\n$altName" + it
                    }
                }
            }
        }

        return manga
    }
}
