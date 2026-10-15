package eu.kanade.tachiyomi.extension.es.inmortalscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class InmortalScan : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("es"))
    override val mangaSubString = "mg"
    override val chapterMode = ChapterMode.MangaAjax

    override val supportsFilterFetching = false

    // Manga Details Selector
    override val mangaDetailsSelectorTitle = "h1"
    override val mangaDetailsSelectorStatus = "span.scanim-series-status"
    override val mangaDetailsSelectorDescription = "div.scanim-series-description p:not(.scanim-seo-info p)"
    override val mangaDetailsSelectorThumbnail = "div.scanim-series-cover img"
    override val mangaDetailsSelectorGenre = "a[href*='manga-genre']"
    override val altNameSelector = "p.scanim-series-alternative"

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"
}
