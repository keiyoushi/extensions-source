package eu.kanade.tachiyomi.extension.id.comic21

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document

class Comic21 : MangaThemesia(
    "Comic 21",
    "https://comic21.me",
    "id",
) {
    override val hasProjectPage = true

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            // Add 'color' badge as a genre
            if (document.selectFirst(".thumb .colored") != null) {
                val genres = genre
                    ?.split(", ")
                    ?.toMutableList()
                    ?: mutableListOf()

                genre = genres
                    .apply { add("Color") }
                    .joinToString()
            }
        }
    }
}
