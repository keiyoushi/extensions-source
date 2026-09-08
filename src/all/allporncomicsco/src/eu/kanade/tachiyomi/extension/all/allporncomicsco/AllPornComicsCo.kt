package eu.kanade.tachiyomi.extension.all.allporncomicsco

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class AllPornComicsCo : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    override val mangaSubString = "comic"
    override val archiveUrlSelector = "h3 > a:not([target=_self]):last-of-type"
}
