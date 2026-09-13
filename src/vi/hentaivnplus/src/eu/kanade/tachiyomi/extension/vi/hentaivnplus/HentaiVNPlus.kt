package eu.kanade.tachiyomi.extension.vi.hentaivnplus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HentaiVNPlus : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    override val mangaSubString = "truyen-hentai"
    override val pageListParseSelector = ".reading-content img"
}
