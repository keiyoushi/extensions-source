package eu.kanade.tachiyomi.extension.es.doujinshell

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DoujinsHell : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM, yyyy", Locale.forLanguageTag("es"))

    override val chapterMode = ChapterMode.MangaAjax

    override val mangaSubString = "doujin"

    // A significant amount of entries are in the wrong category
    override val filterNonMangaItems = false

    // .aligncenter: Next / Prev / PDF buttons
    override val pageListParseSelector = ".reading-content img:not(.aligncenter)"

    override fun chapterListSelector() = "div.listing-chapters_wrap li.wp-manga-chapter"

    override fun parseChapterList(document: Document, mangaPath: String) = super.parseChapterList(document, mangaPath).apply {
        if (size == 1) first().name = "Capítulo"
    }

    override fun parsePages(document: Document) = super.parsePages(document).also { pages ->
        if (pages.isEmpty() && document.select(".reading-content iframe").isNotEmpty()) {
            throw Exception("No se admiten vídeos")
        }
    }
}
