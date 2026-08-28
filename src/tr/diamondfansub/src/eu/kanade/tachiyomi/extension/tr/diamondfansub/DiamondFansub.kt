package eu.kanade.tachiyomi.extension.tr.diamondfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DiamondFansub : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("tr-TR"))
    override val chapterDateSelector = ".chapter-release-date .timediff"
    override val mangaSubString = "seri"
    override val chapterMode = ChapterMode.MangaAjax
    override val mangaDetailsSelectorAuthor = ".manga-authors"
    override val mangaDetailsSelectorDescription = ".manga-info"
}
