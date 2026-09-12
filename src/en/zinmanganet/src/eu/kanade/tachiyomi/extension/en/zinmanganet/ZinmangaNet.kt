package eu.kanade.tachiyomi.extension.en.zinmanganet

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ZinmangaNet : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ROOT)
    override val chapterMode = ChapterMode.MangaAjax

    override val filterNonMangaItems = false
}
