package eu.kanade.tachiyomi.extension.fr.scanhentaimenu

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class XManga : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.FRENCH)
    override val chapterMode = ChapterMode.MangaAjax
    override val pageListParseSelector = "div.reading-content img"
}
