package eu.kanade.tachiyomi.extension.pt.tiamanhwa

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TiaManhwa : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
    override val mangaSubString = "manhwa"
    override val genreDirectory = "tag-manhwa"
}
