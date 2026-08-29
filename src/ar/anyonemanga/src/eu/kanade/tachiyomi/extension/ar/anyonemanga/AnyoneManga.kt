package eu.kanade.tachiyomi.extension.ar.anyonemanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class AnyoneManga : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ROOT)
    override val chapterMode = ChapterMode.MangaAjax

    override fun archiveSelector() = ".am-manga-card"
    override fun searchCardSelector() = archiveSelector()
    override val archiveUrlSelector = ".am-manga-card__title a"
    override val mangaDetailsSelectorTitle = ".am-manga-hero__title"
    override val mangaDetailsSelectorAuthor = ".am-manga-meta tr:has(td:matchesOwn(Author\\(s\\))) td:nth-child(2) a"
    override val mangaDetailsSelectorArtist = ".am-manga-meta tr:has(td:matchesOwn(Artist\\(s\\))) td:nth-child(2) a"
    override val mangaDetailsSelectorStatus = ".am-manga-meta tr:has(td:matchesOwn(Status)) td:nth-child(2)"
    override val mangaDetailsSelectorDescription = ".am-manga-summary__text"
    override val mangaDetailsSelectorThumbnail = ".am-manga-hero__cover img"
    override val mangaDetailsSelectorGenre = ".am-genres-wrap a"
    override val seriesTypeSelector = ".am-manga-meta tr:has(td:matchesOwn(Type)) td:nth-child(2)"
    override val altNameSelector = ".am-manga-meta tr:has(td:matchesOwn(Alternative)) td:nth-child(2)"

    override fun parseArchive(document: Document) = document.select(archiveSelector()).mapNotNull { element ->
        val link = element.selectFirst(archiveUrlSelector) ?: return@mapNotNull null
        val slug = link.attr("abs:href").toHttpUrl().pathSegments.lastOrNull(String::isNotBlank) ?: return@mapNotNull null
        archiveManga(element, slug)
    }

    override fun imageFromElement(element: Element): String? = element.attr("abs:data-encrypted-src").ifBlank { super.imageFromElement(element) }
}
