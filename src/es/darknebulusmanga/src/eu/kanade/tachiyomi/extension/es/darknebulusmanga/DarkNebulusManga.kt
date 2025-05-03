package eu.kanade.tachiyomi.extension.es.darknebulusmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class DarkNebulusManga : Madara(
    "Dark Nebulus Manga",
    "https://darknebulusmanga.com",
    "es",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorAuthor = "strong:contains(Autor) + span a"
    override val mangaDetailsSelectorArtist = "strong:contains(Artista) + span a"
    override val mangaDetailsSelectorDescription = ".manga-summary"
    override val mangaDetailsSelectorThumbnail = "head meta[property=og:image]"

    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("content") -> element.attr("abs:content")
            else -> super.imageFromElement(element)
        }
    }
}
