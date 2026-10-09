package eu.kanade.tachiyomi.extension.vi.jellycomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class JellyComics : Madara() {
    override val mangaSubString = "truyen"
    override val genreDirectory = "the-loai"
    override val supportsPostId = false

    override fun archiveSelector() = ".comic-list-item"
    override val archiveUrlSelector = "a.comic-block-link"
    override val archiveTitleSelector = "h3.comic-block-title"

    override val mangaDetailsSelectorTitle = "h2.comic-title"
    override val mangaDetailsSelectorThumbnail = ".comic-desc-img"
    override val mangaDetailsSelectorStatus = "li:has(strong:containsOwn(Trạng thái))"
    override val mangaDetailsSelectorGenre = "li:has(strong:containsOwn(Thể loại)) a"
    override val mangaDetailsSelectorTag = "li:has(strong:containsOwn(Thẻ)) a"
    override val altNameSelector = "li:has(strong:containsOwn(Tên khác))"

    override fun imageFromElement(element: Element): String? = when {
        element.hasAttr("data-bg") -> element.attr("abs:data-bg")
        else -> super.imageFromElement(element)
    }

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        author = document.selectFirst("li:has(strong:containsOwn(Tác giả))")
            ?.ownText()?.trim()
            ?.takeIf { it.isNotEmpty() && !isUpdating(it) }
    }

    override fun chapterListSelector() = "li.wp-manga-chapter, div.chapter-item"
    override val chapterNameSelector = "p.chapter-title"
    override val chapterDateSelector = "span.chapter-release-date, p.chapter-meta i:last-child"

    override val chapterMode = ChapterMode.MangaPage
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH)

    override fun parsePages(document: Document): List<Page> = document.select(".reading-content img.manga-page").mapIndexedNotNull { index, img ->
        imageFromElement(img)?.let { Page(index, imageUrl = it) }
    }
}
