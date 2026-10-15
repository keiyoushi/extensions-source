package eu.kanade.tachiyomi.extension.th.doujinza

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DoujinZa : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("th"))

    override val mangaSubString = "doujin"
    override val genreDirectory = "doujin-genre"
}
