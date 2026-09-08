package eu.kanade.tachiyomi.extension.en.manhuatop

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ManhuaTop : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ROOT)

    override val mangaSubString = "manhua"
    override val filterNonMangaItems = false
}
