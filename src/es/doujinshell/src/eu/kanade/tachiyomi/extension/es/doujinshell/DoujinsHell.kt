package eu.kanade.tachiyomi.extension.es.doujinshell

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class DoujinsHell : Madara(
    "DoujinsHell",
    "https://www.doujinshell.com",
    "es",
    dateFormat = SimpleDateFormat("d MMMM, yyyy", Locale("es")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val mangaSubString = "doujin"
    override val fetchGenres = false

    // A significant amount of entries are in the wrong category
    override val filterNonMangaItems = false

    // .aligncenter: Next / Prev / PDF buttons
    override val pageListParseSelector = ".reading-content noscript img:not(.aligncenter)"

    override fun chapterListSelector() = "div.listing-chapters_wrap li.wp-manga-chapter"

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).apply {
            if (size == 1) first().name = "Capítulo"
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return super.pageListParse(document).also { pages ->
            if (pages.isEmpty() && document.select(".reading-content iframe").isNotEmpty()) {
                throw Exception("No se admiten vídeos")
            }
        }
    }
}
